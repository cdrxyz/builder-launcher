import {
	AI_TIMEOUT_MS,
	SYSTEM,
	XAI_OAUTH,
	anthropicTools,
	calculate,
	chatRoot,
	continueAnthropic,
	continueOpenAi,
	droppedFields,
	isPublicHttps,
	llmError,
	openaiTools,
	parseModelTurn,
	parseRefresh,
	performTool,
	planUpstream,
	privacyExtra,
	publicPageUrl,
	readHermesSse,
	searchActivity,
	searchUrl,
	looksLikeErrorPage,
	chatDisplay,
	hostOf,
	stripHtml,
	toolStep,
} from '../../website/public/web/ai.js';
import { jsonError } from './safe';

type Turn = { role: string; content: string };
type Plan = {
	error?: string;
	provider?: string;
	kind: string;
	url: string;
	model: string;
	bearer: string;
	oauth: boolean;
	extraHeaders: Record<string, string>;
	messages: Turn[];
	webUrl: string;
	apiBase: string;
	keyOptional: boolean;
};

export function planChat(body: Record<string, unknown>): Plan {
	return planUpstream(body) as Plan;
}

export async function handleAi(request: Request, url: URL): Promise<Response | null> {
	if (url.pathname === '/api/ai/chat' && request.method === 'POST') return aiChat(request);
	if (url.pathname === '/api/ai/refresh' && request.method === 'POST') return aiRefresh(request);
	return null;
}

async function aiChat(request: Request): Promise<Response> {
	const body = (await request.json().catch(() => null)) as Record<string, unknown> | null;
	if (!body) return jsonError('Invalid JSON');
	const plan = planChat(body);
	if (plan.error) return jsonError(plan.error);
	if (!plan.bearer && plan.kind !== 'hermes' && !plan.keyOptional) {
		return jsonError('Sign in or paste an API key in settings.');
	}
	const encoder = new TextEncoder();
	const stream = new ReadableStream({
		async start(controller) {
			const send = (event: string, data: unknown) => {
				controller.enqueue(encoder.encode(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`));
			};
			const onProgress = (lines: string[]) => {
				if (lines.length) send('search', { lines });
			};
			try {
				const text =
					plan.kind === 'hermes'
						? await hermesAsk(plan)
						: plan.kind === 'anthropic'
							? await anthropicAsk(plan, onProgress)
							: await openaiAsk(plan, onProgress);
				send('done', { text: chatDisplay(text) });
			} catch {
				send('done', { text: 'Could not reach the model.' });
			}
			controller.close();
		},
	});
	return new Response(stream, {
		headers: { 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-store' },
	});
}

async function aiRefresh(request: Request): Promise<Response> {
	const body = (await request.json().catch(() => null)) as { provider?: string; refreshToken?: string } | null;
	if (String(body?.provider || '').toUpperCase() !== 'XAI') return jsonError('This provider has no web refresh.');
	const refresh = String(body?.refreshToken || '');
	if (!refresh) return jsonError('Refresh token is required');
	const form = new URLSearchParams({
		grant_type: 'refresh_token',
		refresh_token: refresh,
		client_id: XAI_OAUTH.clientId,
	});
	const res = await fetch(XAI_OAUTH.tokenUrl, {
		method: 'POST',
		headers: { 'content-type': 'application/x-www-form-urlencoded', accept: 'application/json' },
		body: form,
		signal: AbortSignal.timeout(AI_TIMEOUT_MS),
	});
	const raw = await res.text();
	if (!res.ok) return jsonError('OAuth refresh failed', 502);
	const tokens = parseRefresh(raw, Date.now(), refresh);
	if (!tokens) return jsonError('OAuth refresh failed', 502);
	return Response.json(tokens, { headers: { 'cache-control': 'no-store' } });
}

const TOOL_ROUNDS = 2;
const TOOL_FETCH_MS = 8_000;
const SEARCH_UA = 'BuilderLauncher/0.1 (+https://cdr.xyz)';

function openaiBody(model: string, messages: unknown[], extra: Record<string, unknown>, tools: boolean) {
	const body: Record<string, unknown> = {
		model,
		messages,
		max_tokens: 2048,
		temperature: 0.4,
		stream: false,
		...extra,
	};
	if (tools) {
		body.tools = openaiTools();
		body.tool_choice = 'auto';
	}
	return JSON.stringify(body);
}

async function openaiAsk(plan: Plan, onProgress?: (lines: string[]) => void): Promise<string> {
	const root = chatRoot(plan.url);
	const headers: Record<string, string> = { 'content-type': 'application/json', accept: 'application/json' };
	if (plan.bearer) headers.authorization = `Bearer ${plan.bearer}`;
	Object.assign(headers, plan.extraHeaders);
	return toolLoop(plan.provider || '', [{ role: 'system', content: SYSTEM }, ...plan.messages], 'openai', async (messages, extra, tools) => {
		const res = await fetch(`${root}/chat/completions`, {
			method: 'POST',
			headers,
			body: openaiBody(plan.model, messages, extra, tools),
			signal: AbortSignal.timeout(AI_TIMEOUT_MS),
		});
		return { ok: res.ok, status: res.status, raw: await res.text() };
	}, onProgress);
}

async function anthropicAsk(plan: Plan, onProgress?: (lines: string[]) => void): Promise<string> {
	const root = plan.url.replace(/\/+$/, '');
	const url = root.endsWith('/v1') ? `${root}/messages` : `${root}/v1/messages`;
	const headers: Record<string, string> = {
		'content-type': 'application/json',
		accept: 'application/json',
		'anthropic-version': '2023-06-01',
	};
	if (plan.bearer) {
		if (plan.oauth) {
			headers.authorization = `Bearer ${plan.bearer}`;
			headers['anthropic-beta'] = 'oauth-2024-10-22';
		} else {
			headers['x-api-key'] = plan.bearer;
		}
	}
	return toolLoop(plan.provider || '', plan.messages, 'anthropic', async (messages, _extra, tools) => {
		const body: Record<string, unknown> = {
			model: plan.model,
			max_tokens: 2048,
			stream: false,
			system: SYSTEM,
			messages,
		};
		if (tools) body.tools = anthropicTools();
		const res = await fetch(url, {
			method: 'POST',
			headers,
			body: JSON.stringify(body),
			signal: AbortSignal.timeout(AI_TIMEOUT_MS),
		});
		return { ok: res.ok, status: res.status, raw: await res.text() };
	}, onProgress);
}

async function toolLoop(
	provider: string,
	start: unknown[],
	kind: 'openai' | 'anthropic',
	post: (messages: unknown[], extra: Record<string, unknown>, tools: boolean) => Promise<{ ok: boolean; status: number; raw: string }>,
	onProgress?: (lines: string[]) => void,
): Promise<string> {
	let messages = start;
	let privacy = true;
	let tools = true;
	let strips = 0;
	let round = 0;
	while (round <= TOOL_ROUNDS) {
		const extra = privacy ? privacyExtra(provider) : {};
		const res = await post(messages, extra, tools);
		if (!res.ok) {
			const drop = res.status === 400 ? droppedFields(res.raw) : { privacy: false, tools: false };
			const canStrip = (drop.privacy && privacy) || (drop.tools && tools);
			if (canStrip && strips < 2) {
				if (drop.privacy) privacy = false;
				if (drop.tools) tools = false;
				strips += 1;
				continue;
			}
			return llmError(res.status, res.raw);
		}
		const turn = parseModelTurn(res.raw, kind);
		if (!turn) return 'Empty reply from the model.';
		const step = toolStep(turn.calls, round, tools, TOOL_ROUNDS);
		if (step === 'answer') return chatDisplay(turn.text || 'Empty reply from the model.');
		const calls = turn.calls.slice(0, 4);
		const started = searchActivity(calls);
		if (started.length) onProgress?.(started);
		const sites: string[] = [];
		let failed = false;
		const results = [];
		for (const call of calls) {
			const outcome = await runTool(call.name, call.arguments);
			sites.push(...outcome.sites);
			if (outcome.failed && (call.name === 'web_search' || call.name === 'web_fetch')) failed = true;
			const lines = searchActivity(calls, sites, failed);
			if (lines.length) onProgress?.(lines);
			results.push({ id: call.id, content: outcome.text });
		}
		messages = kind === 'anthropic' ? continueAnthropic(messages, turn, results) : continueOpenAi(messages, turn, results);
		if (step === 'finish') tools = false;
		else round += 1;
	}
	return 'Empty reply from the model.';
}

type ToolOutcome = { text: string; sites: string[]; failed: boolean };

async function runTool(name: string, argsJson: string): Promise<ToolOutcome> {
	let args: Record<string, unknown> = {};
	try {
		const parsed = JSON.parse(argsJson || '{}');
		if (parsed && typeof parsed === 'object') args = parsed as Record<string, unknown>;
	} catch {
		return { text: 'Invalid tool arguments.', sites: [], failed: true };
	}
	if (name === 'web_search') return searchWeb(String(args.query || ''));
	if (name === 'web_fetch') {
		const raw = String(args.url || '');
		const text = await fetchPage(raw);
		const host = hostOf(publicPageUrl(raw));
		const failed = text === 'Page could not be read.' || text === 'Only public HTTPS pages.';
		return { text, sites: host ? [host] : [], failed };
	}
	if (name === 'calculate') return { text: calculate(String(args.expression || '')), sites: [], failed: false };
	return { text: 'Unknown tool.', sites: [], failed: true };
}

async function searchWeb(query: string): Promise<ToolOutcome> {
	const url = searchUrl(query);
	if (!url) return { text: 'Need a query.', sites: [], failed: true };
	try {
		const res = await fetch(url, {
			headers: { 'user-agent': SEARCH_UA, accept: 'text/html' },
			signal: AbortSignal.timeout(TOOL_FETCH_MS),
		});
		if (!res.ok) return { text: 'Search failed.', sites: [], failed: true };
		const html = await res.text();
		return performTool('web_search', JSON.stringify({ query }), () => html);
	} catch {
		return { text: 'Search failed.', sites: [], failed: true };
	}
}

async function fetchPage(raw: string): Promise<string> {
	let url = publicPageUrl(raw);
	if (!url) return 'Only public HTTPS pages.';
	try {
		for (let hop = 0; hop < 3; hop++) {
			const res = await fetch(url, {
				headers: { 'user-agent': SEARCH_UA, accept: 'text/html,text/plain,application/json' },
				redirect: 'manual',
				signal: AbortSignal.timeout(TOOL_FETCH_MS),
			});
			if (res.status >= 300 && res.status < 400) {
				const next = publicPageUrl(new URL(res.headers.get('location') || '', url).toString());
				if (!next) return 'Only public HTTPS pages.';
				url = next;
				continue;
			}
			if (!res.ok) return 'Page could not be read.';
			const type = res.headers.get('content-type') || '';
			if (type && !/text\/|json|xml/.test(type)) return 'Page is not text.';
			const bytes = new Uint8Array(await res.arrayBuffer());
			const text = new TextDecoder().decode(bytes.subarray(0, 80_000));
			if (looksLikeErrorPage(text)) return 'Page could not be read.';
			const plain = /json|xml/.test(type) ? text : stripHtml(text);
			return plain.replace(/\s+/g, ' ').trim().slice(0, 6000) || 'Page was empty.';
		}
		return 'Page could not be read.';
	} catch {
		return 'Page could not be read.';
	}
}

class CookieJar {
	private readonly rows = new Map<string, string>();

	store(res: Response) {
		const lines = res.headers.getSetCookie?.() || [];
		for (const line of lines) {
			const pair = line.split(';')[0];
			const eq = pair.indexOf('=');
			if (eq <= 0) continue;
			this.rows.set(pair.slice(0, eq), pair.slice(eq + 1));
		}
	}

	header(): string {
		return [...this.rows.entries()].map(([k, v]) => `${k}=${v}`).join('; ');
	}
}

async function hermesAsk(plan: Plan): Promise<string> {
	const root = (plan.webUrl || plan.url).replace(/\/+$/, '');
	if (!isPublicHttps(root)) return 'The web app can only reach a public HTTPS host. A LAN box stays on the phone.';
	const jar = new CookieJar();
	const password = plan.bearer;
	const status = await hermesFetch(jar, `${root}/api/auth/status`, password);
	if (!status) return 'Could not reach the Web UI.';
	const authed = status.code >= 200 && status.code < 300 && /"authenticated"\s*:\s*true/.test(status.body);
	const needsLogin = !authed && (status.code === 401 || status.code === 403 || /"password_required"\s*:\s*true/.test(status.body));
	if (needsLogin) {
		if (!password) return 'Web UI needs a password in settings.';
		const login = await hermesFetch(jar, `${root}/api/auth/login`, password, JSON.stringify({ password }));
		if (!login) return 'Could not reach the Web UI.';
		if (login.code === 401 || login.code === 403) return 'Web UI password was rejected.';
		if (login.code < 200 || login.code >= 300) return llmError(login.code, login.body);
	}
	const model = plan.model && plan.model !== 'default' ? JSON.stringify({ model: plan.model }) : '{}';
	const session = await hermesFetch(jar, `${root}/api/session/new`, password, model);
	if (!session) return 'Could not reach the Web UI.';
	if (session.code < 200 || session.code >= 300) return llmError(session.code, session.body);
	const sessionId = jsonField(session.body, 'session_id');
	if (!sessionId) return 'Web UI did not return a session.';
	const message = plan.messages.map((row) => `${row.role === 'user' ? 'User' : 'Assistant'}: ${row.content}`).join('\n\n');
	const prompt = plan.messages.length === 1 ? plan.messages[0].content : message;
	const start = await hermesFetch(
		jar,
		`${root}/api/chat/start`,
		password,
		JSON.stringify({ session_id: sessionId, message: prompt }),
	);
	if (!start) return 'Could not reach the Web UI.';
	if (start.code < 200 || start.code >= 300) return llmError(start.code, start.body);
	const streamId = jsonField(start.body, 'stream_id');
	if (!streamId) return 'Web UI did not return a stream.';
	const stream = await hermesFetch(jar, `${root}/api/chat/stream?stream_id=${encodeURIComponent(streamId)}`, password);
	if (!stream) return 'Could not reach the Web UI.';
	if (stream.code < 200 || stream.code >= 300) return llmError(stream.code, stream.body);
	return readHermesSse(stream.body);
}

function jsonField(raw: string, key: string): string {
	try {
		const obj = JSON.parse(raw);
		const direct = obj?.[key];
		if (typeof direct === 'string' && direct) return direct;
		const nested = obj?.session?.[key];
		return typeof nested === 'string' ? nested : '';
	} catch {
		return '';
	}
}

async function hermesFetch(jar: CookieJar, url: string, password: string, body?: string): Promise<{ code: number; body: string } | null> {
	const headers: Record<string, string> = { accept: body ? 'application/json' : 'text/event-stream' };
	if (password) headers.authorization = `Bearer ${password}`;
	const cookie = jar.header();
	if (cookie) headers.cookie = cookie;
	if (body) headers['content-type'] = 'application/json';
	try {
		const res = await fetch(url, {
			method: body ? 'POST' : 'GET',
			headers,
			body,
			signal: AbortSignal.timeout(AI_TIMEOUT_MS),
		});
		jar.store(res);
		return { code: res.status, body: await res.text() };
	} catch {
		return null;
	}
}

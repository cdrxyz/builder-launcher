import {
	AI_TIMEOUT_MS,
	SYSTEM,
	XAI_OAUTH,
	chatRoot,
	isPublicHttps,
	llmError,
	parseChatPayload,
	parseRefresh,
	planUpstream,
	readHermesSse,
} from '../../website/public/web/ai.js';
import { jsonError } from './safe';

type Turn = { role: string; content: string };
type Plan = {
	error?: string;
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
	try {
		const text =
			plan.kind === 'hermes'
				? await hermesAsk(plan)
				: plan.kind === 'anthropic'
					? await anthropicAsk(plan)
					: await openaiAsk(plan);
		return Response.json({ text }, { headers: { 'cache-control': 'no-store' } });
	} catch {
		return Response.json({ text: 'Could not reach the model.' }, { headers: { 'cache-control': 'no-store' } });
	}
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

function openaiBody(model: string, messages: Turn[]) {
	return JSON.stringify({
		model,
		messages: [{ role: 'system', content: SYSTEM }, ...messages],
		max_tokens: 2048,
		temperature: 0.4,
		stream: false,
	});
}

async function openaiAsk(plan: Plan): Promise<string> {
	const root = chatRoot(plan.url);
	const headers: Record<string, string> = { 'content-type': 'application/json', accept: 'application/json' };
	if (plan.bearer) headers.authorization = `Bearer ${plan.bearer}`;
	Object.assign(headers, plan.extraHeaders);
	const res = await fetch(`${root}/chat/completions`, {
		method: 'POST',
		headers,
		body: openaiBody(plan.model, plan.messages),
		signal: AbortSignal.timeout(AI_TIMEOUT_MS),
	});
	const raw = await res.text();
	if (!res.ok) return llmError(res.status, raw);
	return parseChatPayload(raw, 'openai') || 'Empty reply from the model.';
}

async function anthropicAsk(plan: Plan): Promise<string> {
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
	const res = await fetch(url, {
		method: 'POST',
		headers,
		body: JSON.stringify({
			model: plan.model,
			max_tokens: 2048,
			stream: false,
			system: SYSTEM,
			messages: plan.messages,
		}),
		signal: AbortSignal.timeout(AI_TIMEOUT_MS),
	});
	const raw = await res.text();
	if (!res.ok) return llmError(res.status, raw);
	return parseChatPayload(raw, 'anthropic') || 'Empty reply from the model.';
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

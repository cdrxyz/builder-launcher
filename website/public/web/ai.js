export const SYSTEM =
	"You are a concise assistant on a builder's phone. Prefer short answers they can act on. Use markdown when it helps: headings, lists, tables, and fenced code. Skip preamble. You can search the web, open a public page, and calculate. Use those for current facts and arithmetic. Do not invent sources.";

export const AI_TIMEOUT_MS = 45_000;
export const OAUTH_SKEW_MS = 120_000;
export const MAX_TURNS = 40;
export const MAX_TURN_CHARS = 16_000;

export const XAI_OAUTH = {
	clientId: 'b1a00492-073a-47ea-816f-4c329264a828',
	tokenUrl: 'https://auth.x.ai/oauth2/token',
};

const FIXED = {
	XAI: { label: 'xAI', apiBase: 'https://api.x.ai/v1', model: 'grok-4.6', kind: 'openai', oauth: 'xai' },
	OPENAI: { label: 'OpenAI', apiBase: 'https://api.openai.com/v1', model: 'gpt-4o', kind: 'openai' },
	ANTHROPIC: { label: 'Anthropic', apiBase: 'https://api.anthropic.com', model: 'claude-sonnet-4-5', kind: 'anthropic' },
	GEMINI: {
		label: 'Gemini',
		apiBase: 'https://generativelanguage.googleapis.com/v1beta/openai',
		model: 'gemini-2.5-flash',
		kind: 'openai',
	},
	OPENROUTER: {
		label: 'OpenRouter',
		apiBase: 'https://openrouter.ai/api/v1',
		model: 'openrouter/auto',
		kind: 'openai',
		extraHeaders: { 'HTTP-Referer': 'https://cdr.xyz', 'X-Title': 'Builder Launcher' },
	},
	GROQ: { label: 'Groq', apiBase: 'https://api.groq.com/openai/v1', model: 'llama-3.3-70b-versatile', kind: 'openai' },
	DEEPSEEK: { label: 'DeepSeek', apiBase: 'https://api.deepseek.com', model: 'deepseek-chat', kind: 'openai' },
	MISTRAL: { label: 'Mistral', apiBase: 'https://api.mistral.ai/v1', model: 'mistral-small-latest', kind: 'openai' },
	HERMES: { label: 'Hermes', apiBase: null, model: 'default', kind: 'hermes', keyOptional: true },
	LMSTUDIO: { label: 'LM Studio', apiBase: null, model: 'local-model', kind: 'openai', keyOptional: true },
	OLLAMA: { label: 'Ollama', apiBase: null, model: 'llama3.2', kind: 'openai', keyOptional: true },
	GENERIC: { label: 'OpenAI API', apiBase: null, model: 'gpt-4o', kind: 'openai' },
};

export function platformOf(provider) {
	return FIXED[String(provider || '').toUpperCase()] || null;
}

export function providerLabel(provider) {
	return platformOf(provider)?.label || String(provider || 'AI');
}

export function questionFromInput(value) {
	const trimmed = String(value || '').trim();
	return trimmed.startsWith('?') ? trimmed.slice(1).trim() : trimmed;
}

export function chatTitle(messages) {
	const first = (messages || []).find((row) => String(row?.role).toLowerCase() === 'user');
	const line = String(first?.content || '')
		.split('\n')
		.find((row) => row.trim());
	const compact = String(line || '').replace(/\s+/g, ' ').trim();
	return (compact || 'untitled').slice(0, 48);
}

export function threadsOf(threads) {
	return (threads || [])
		.filter((row) => row && row.id && Array.isArray(row.messages) && row.messages.length)
		.slice()
		.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
}

export function editedLabel(millis) {
	const when = new Date(millis);
	if (Number.isNaN(when.getTime())) return '';
	return when.toLocaleString([], { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export function chatRoot(base) {
	const trimmed = String(base || '').trim().replace(/\/+$/, '');
	if (!trimmed) return '';
	if (trimmed.endsWith('/v1') || trimmed.endsWith('/openai')) return trimmed;
	return `${trimmed}/v1`;
}

export function isPublicHttps(raw) {
	let url;
	try {
		url = new URL(String(raw || '').trim());
	} catch {
		return false;
	}
	if (url.protocol !== 'https:') return false;
	if (url.username || url.password) return false;
	const host = url.hostname.toLowerCase();
	if (!host || host === 'localhost' || host.endsWith('.local') || host === 'metadata.google.internal') return false;
	if (host.includes(':')) return false;
	const parts = host.split('.');
	if (parts.length === 4 && parts.every((part) => /^\d+$/.test(part))) {
		const a = Number(parts[0]);
		const b = Number(parts[1]);
		if (a === 10 || a === 127 || a === 0 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31) || (a === 169 && b === 254)) {
			return false;
		}
	}
	return true;
}

export function tokensOf(settings) {
	const access = String(settings?.oauthAccess || '');
	const refresh = String(settings?.oauthRefresh || '');
	if (!access && !refresh) return null;
	return {
		access,
		refresh,
		expiresAt: Number(settings?.oauthExpiresAtEpochMs || 0),
		account: String(settings?.oauthAccount || ''),
	};
}

export function oauthValid(settings, now = Date.now()) {
	const tokens = tokensOf(settings);
	if (!tokens || !tokens.access) return false;
	return tokens.expiresAt - OAUTH_SKEW_MS > now;
}

export function needsRefresh(settings, now = Date.now()) {
	const tokens = tokensOf(settings);
	if (!tokens || !tokens.refresh) return false;
	return !oauthValid(settings, now);
}

export function bearerOf(settings, now = Date.now()) {
	if (oauthValid(settings, now)) return tokensOf(settings).access;
	const key = String(settings?.apiKey || '').trim();
	return key || null;
}

export function usesOauthBearer(settings, now = Date.now()) {
	return oauthValid(settings, now);
}

function baseOf(settings) {
	const provider = String(settings?.provider || '').toUpperCase();
	if (provider === 'HERMES') return String(settings?.hermesWebUrl || settings?.hermesBaseUrl || '').trim();
	return String(settings?.hermesBaseUrl || '').trim();
}

export function missingCreds(settings) {
	const provider = String(settings?.provider || 'HERMES').toUpperCase();
	const platform = platformOf(provider);
	if (provider === 'HERMES') return 'Set a Web UI URL in settings.';
	if (platform && !platform.apiBase && platform.keyOptional) return 'Set a base URL in settings.';
	if (platform && !platform.apiBase) return 'Set a base URL and API key in settings.';
	return 'Sign in or paste an API key in settings. Turn on Include AI credentials on the phone and sync.';
}

export function readyForAsk(settings, now = Date.now()) {
	const provider = String(settings?.provider || '').toUpperCase();
	const platform = platformOf(provider);
	if (!platform) return false;
	if (!platform.apiBase && !baseOf(settings)) return false;
	if (platform.keyOptional) return true;
	if (bearerOf(settings, now)) return true;
	return Boolean(tokensOf(settings)?.refresh);
}

export function accessLabel(settings, now = Date.now()) {
	const provider = providerLabel(settings?.provider);
	if (oauthValid(settings, now)) {
		const account = tokensOf(settings)?.account;
		return account ? `${provider} · signed in as ${account}` : `${provider} · signed in`;
	}
	if (needsRefresh(settings, now)) return `${provider} · OAuth refresh on the next question`;
	if (String(settings?.apiKey || '').trim()) return `${provider} · API key in snapshot`;
	if (platformOf(settings?.provider)?.keyOptional && baseOf(settings)) return `${provider} · ${baseOf(settings)}`;
	return `${provider} · no credentials in this snapshot`;
}

export function turnsOf(messages) {
	return (messages || [])
		.filter((row) => row && String(row.content || '').trim() && String(row.role).toLowerCase() !== 'notice')
		.slice(-MAX_TURNS)
		.map((row) => ({
			role: String(row.role).toLowerCase() === 'user' ? 'user' : 'assistant',
			content: String(row.content).slice(0, MAX_TURN_CHARS),
		}));
}

export function chatPlan(settings, messages, now = Date.now()) {
	const provider = String(settings?.provider || 'HERMES').toUpperCase();
	const platform = platformOf(provider);
	if (!platform) return { error: 'Unknown provider.' };
	const turns = turnsOf(messages);
	if (!turns.length) return { error: 'Ask a question.' };
	if (!readyForAsk(settings, now)) return { error: missingCreds(settings) };
	const base = platform.apiBase || baseOf(settings);
	if (!platform.apiBase && !isPublicHttps(base)) {
		return { error: 'The web app can only reach a public HTTPS host. A LAN box stays on the phone.' };
	}
	const bearer = bearerOf(settings, now);
	if (!platform.keyOptional && !bearer && !needsRefresh(settings, now)) return { error: missingCreds(settings) };
	return {
		provider,
		kind: platform.kind,
		base,
		webUrl: String(settings?.hermesWebUrl || '').trim(),
		apiBase: String(settings?.hermesBaseUrl || '').trim(),
		model: String(settings?.model || '').trim() || platform.model,
		bearer: bearer || '',
		oauth: usesOauthBearer(settings, now),
		extraHeaders: platform.extraHeaders || {},
		messages: turns,
	};
}

export function refreshPlan(settings) {
	const provider = String(settings?.provider || '').toUpperCase();
	if (provider !== 'XAI') return null;
	const refresh = tokensOf(settings)?.refresh || '';
	if (!refresh) return null;
	return { provider, refreshToken: refresh };
}

export function applyRefresh(settings, tokens) {
	const next = { ...(settings || {}) };
	if (!tokens?.accessToken) return next;
	next.oauthAccess = tokens.accessToken;
	next.oauthRefresh = tokens.refreshToken || next.oauthRefresh || '';
	next.oauthExpiresAtEpochMs = Number(tokens.expiresAtEpochMs || 0);
	if (tokens.account) next.oauthAccount = tokens.account;
	return next;
}

export function parseChatPayload(raw, kind) {
	let root;
	try {
		root = JSON.parse(raw);
	} catch {
		return null;
	}
	if (kind === 'anthropic') {
		const text = root?.content?.[0]?.text;
		return typeof text === 'string' ? text : null;
	}
	const text = root?.choices?.[0]?.message?.content;
	return typeof text === 'string' ? text : null;
}

export function llmError(code, raw) {
	return `LLM error ${code}: ${shortReason(raw)}`.trim();
}

export function shortReason(raw) {
	let flat = '';
	try {
		const root = JSON.parse(String(raw || ''));
		const message = typeof root?.message === 'string' ? root.message : '';
		const err = root?.error;
		const fromErr = typeof err === 'string' ? err : typeof err?.message === 'string' ? err.message : '';
		flat = String(message || fromErr).replace(/\s+/g, ' ').trim();
	} catch {
		flat = '';
	}
	if (flat.length >= 1 && flat.length <= 60 && !/[<{]/.test(flat)) return flat;
	return 'request failed';
}

export function shortFailure(raw) {
	const flat = String(raw || '')
		.replace(/<[^>]+>/g, ' ')
		.replace(/\s+/g, ' ')
		.trim();
	if (flat.length >= 1 && flat.length <= 60 && !flat.includes('<') && !flat.includes('{') && !/http/i.test(flat)) {
		return flat;
	}
	return 'Search failed.';
}

export function chatDisplay(source) {
	const text = String(source ?? '');
	const trimmed = text.trim();
	if (!trimmed) return text;
	if (isDump(trimmed)) return looksLikeSearchDump(trimmed) ? 'Search failed.' : 'Could not reach the model.';
	if (trimmed.length > 12000) return `${trimmed.slice(0, 12000).trimEnd()}…`;
	return text;
}

function isDump(text) {
	const lower = text.toLowerCase();
	if (lower.includes('<html') || lower.includes('<!doctype')) return true;
	if (text.startsWith('LLM error') && text.length > 160) return true;
	if (text.startsWith('{') && text.length > 400) return true;
	return text.length > 12000;
}

function looksLikeSearchDump(text) {
	const lower = text.toLowerCase();
	return lower.includes('<html') || lower.includes('<!doctype') || lower.includes('duckduckgo') || lower.includes('search failed');
}

export function parseRefresh(raw, now, previousRefresh) {
	let root;
	try {
		root = JSON.parse(raw);
	} catch {
		return null;
	}
	const access = String(root?.access_token || '');
	if (!access) return null;
	const expiresIn = Math.max(60, Number(root.expires_in || 3600));
	return {
		accessToken: access,
		refreshToken: String(root.refresh_token || previousRefresh || ''),
		expiresAtEpochMs: now + expiresIn * 1000,
		account: String(root.email || root.account || ''),
	};
}

export function readHermesSse(raw) {
	const frames = String(raw || '').split(/\n\n+/);
	let text = '';
	let error = '';
	for (const frame of frames) {
		if (!frame.trim()) continue;
		const event = frame
			.split('\n')
			.find((line) => line.startsWith('event:'))
			?.slice(6)
			.trim() || 'message';
		const data = frame
			.split('\n')
			.filter((line) => line.startsWith('data:'))
			.map((line) => line.slice(5).trimStart())
			.join('\n');
		if (event === 'error') {
			try {
				const obj = JSON.parse(data);
				error = obj.error || obj.message || 'Web UI stream error.';
			} catch {
				error = data || 'Web UI stream error.';
			}
			continue;
		}
		if (event === 'token' || event === 'message') {
			try {
				const piece = JSON.parse(data)?.text;
				if (typeof piece === 'string') text += piece;
			} catch {
				/* ignore non-json keepalive */
			}
		}
	}
	if (error) return error;
	return text || 'Empty reply from the model.';
}

export function planUpstream(body) {
	const provider = String(body?.provider || '').toUpperCase();
	const platform = platformOf(provider);
	if (!platform) return { error: 'Unknown provider.' };
	const messages = (body?.messages || [])
		.filter((row) => row && (row.role === 'user' || row.role === 'assistant') && String(row.content || '').trim())
		.slice(0, MAX_TURNS)
		.map((row) => ({ role: row.role, content: String(row.content).slice(0, MAX_TURN_CHARS) }));
	if (!messages.length) return { error: 'Ask a question.' };
	const base = platform.apiBase || String(body?.base || '').trim();
	if (!base) return { error: 'Set a base URL in settings.' };
	if (!platform.apiBase && !isPublicHttps(base)) {
		return { error: 'The web app can only reach a public HTTPS host. A LAN box stays on the phone.' };
	}
	if (platform.apiBase && String(body?.base || '').trim() && String(body.base).replace(/\/+$/, '') !== platform.apiBase.replace(/\/+$/, '')) {
		return { error: 'Host not allowed' };
	}
	const extra = {};
	for (const [key, value] of Object.entries(body?.extraHeaders || {})) {
		if ((key === 'HTTP-Referer' || key === 'X-Title') && typeof value === 'string' && value.length < 200) extra[key] = value;
	}
	const webUrl = String(body?.webUrl || '').trim();
	const apiBase = String(body?.apiBase || '').trim();
	if (webUrl && !isPublicHttps(webUrl)) return { error: 'The web app can only reach a public HTTPS host. A LAN box stays on the phone.' };
	if (apiBase && !isPublicHttps(apiBase)) return { error: 'The web app can only reach a public HTTPS host. A LAN box stays on the phone.' };
	return {
		provider,
		kind: platform.kind,
		url: base,
		model: String(body?.model || platform.model).slice(0, 120),
		bearer: String(body?.bearer || ''),
		oauth: Boolean(body?.oauth),
		extraHeaders: extra,
		messages,
		webUrl,
		apiBase,
		keyOptional: Boolean(platform.keyOptional),
	};
}

export function upsertThread(threads, thread) {
	const list = Array.isArray(threads) ? threads.filter((row) => row?.id !== thread.id) : [];
	return [thread, ...list];
}

export function dropThread(threads, id) {
	return (threads || []).filter((row) => String(row?.id) !== String(id));
}

const STORE_FALSE = new Set(['OPENAI', 'GENERIC', 'GEMINI', 'GROQ', 'XAI', 'DEEPSEEK', 'MISTRAL']);

const TOOL_DEFS = [
	{
		name: 'web_search',
		description: 'Search the public web. Use for current facts, news, and anything you are not sure of.',
		schema: {
			type: 'object',
			properties: { query: { type: 'string', description: 'Search query' } },
			required: ['query'],
			additionalProperties: false,
		},
	},
	{
		name: 'web_fetch',
		description: 'Read the text of one public HTTPS page.',
		schema: {
			type: 'object',
			properties: { url: { type: 'string', description: 'https URL' } },
			required: ['url'],
			additionalProperties: false,
		},
	},
	{
		name: 'calculate',
		description: 'Evaluate an arithmetic expression. No variables.',
		schema: {
			type: 'object',
			properties: { expression: { type: 'string', description: 'Arithmetic expression' } },
			required: ['expression'],
			additionalProperties: false,
		},
	},
];

export function privacyExtra(provider) {
	const id = String(provider || '').toUpperCase();
	if (id === 'OPENROUTER') return { provider: { zdr: true, data_collection: 'deny' } };
	if (STORE_FALSE.has(id)) return { store: false };
	return {};
}

export function openaiTools() {
	return TOOL_DEFS.map((tool) => ({
		type: 'function',
		function: { name: tool.name, description: tool.description, parameters: tool.schema },
	}));
}

export function anthropicTools() {
	return TOOL_DEFS.map((tool) => ({
		name: tool.name,
		description: tool.description,
		input_schema: tool.schema,
	}));
}

export function parseModelTurn(raw, kind) {
	let root;
	try {
		root = JSON.parse(raw);
	} catch {
		return null;
	}
	if (kind === 'anthropic') {
		const blocks = Array.isArray(root?.content) ? root.content : [];
		const text = blocks
			.filter((block) => block?.type === 'text' && typeof block.text === 'string')
			.map((block) => block.text)
			.join('');
		const calls = blocks
			.filter((block) => block?.type === 'tool_use' && block.name)
			.map((block, index) => ({
				id: String(block.id || `toolu_${index}`),
				name: String(block.name),
				arguments: JSON.stringify(block.input && typeof block.input === 'object' ? block.input : {}),
			}));
		if (!text && !calls.length) return null;
		return { text, calls, blocks };
	}
	const message = root?.choices?.[0]?.message;
	if (!message) return null;
	const text = typeof message.content === 'string' ? message.content : '';
	const calls = (Array.isArray(message.tool_calls) ? message.tool_calls : [])
		.filter((call) => call?.function?.name)
		.map((call, index) => ({
			id: String(call.id || `call_${index}`),
			name: String(call.function.name),
			arguments:
				typeof call.function.arguments === 'string'
					? call.function.arguments
					: JSON.stringify(call.function.arguments || {}),
		}));
	if (!text && !calls.length) return null;
	return { text, calls, blocks: [] };
}

export function continueOpenAi(messages, turn, results) {
	const calls = (turn?.calls || []).map((call) => ({
		id: call.id,
		type: 'function',
		function: { name: call.name, arguments: call.arguments || '{}' },
	}));
	const assistant = { role: 'assistant', content: turn?.text || null, tool_calls: calls };
	const rows = (results || []).map((row) => ({
		role: 'tool',
		tool_call_id: row.id,
		content: String(row.content || '').slice(0, 6000),
	}));
	return [...(messages || []), assistant, ...rows];
}

export function continueAnthropic(messages, turn, results) {
	const blocks =
		Array.isArray(turn?.blocks) && turn.blocks.length
			? turn.blocks
			: [
					...(turn?.text ? [{ type: 'text', text: turn.text }] : []),
					...(turn?.calls || []).map((call) => ({
						type: 'tool_use',
						id: call.id,
						name: call.name,
						input: jsonObject(call.arguments),
					})),
				];
	return [
		...(messages || []),
		{ role: 'assistant', content: blocks },
		{
			role: 'user',
			content: (results || []).map((row) => ({
				type: 'tool_result',
				tool_use_id: row.id,
				content: String(row.content || '').slice(0, 6000),
			})),
		},
	];
}

function jsonObject(raw) {
	try {
		const value = JSON.parse(raw || '{}');
		return value && typeof value === 'object' ? value : {};
	} catch {
		return {};
	}
}

export function droppedFields(errorText) {
	const lower = String(errorText || '').toLowerCase();
	const unknown = /unknown|unrecognized|unexpected|not supported|not a valid|invalid parameter|extra field|additional propert/.test(lower);
	if (!unknown) return { privacy: false, tools: false };
	return {
		privacy: /store|data_collection|\bzdr\b|provider/.test(lower),
		tools: /tool/.test(lower),
	};
}

export function publicPageUrl(raw) {
	const value = String(raw || '').trim();
	if (!isPublicHttps(value)) return '';
	try {
		const url = new URL(value);
		url.hash = '';
		return url.toString();
	} catch {
		return '';
	}
}

function unwrapDdg(href) {
	const decoded = String(href || '').replace(/&amp;/g, '&');
	try {
		const url = new URL(decoded, 'https://duckduckgo.com');
		const uddg = url.searchParams.get('uddg');
		if (uddg) return uddg;
		if (url.protocol === 'https:' && !url.hostname.endsWith('duckduckgo.com')) return url.toString();
	} catch {
		return '';
	}
	return '';
}

function decodeEntities(text) {
	return String(text || '')
		.replace(/<[^>]+>/g, ' ')
		.replace(/&amp;/g, '&')
		.replace(/&quot;/g, '"')
		.replace(/&#39;|&apos;/g, "'")
		.replace(/&lt;/g, '<')
		.replace(/&gt;/g, '>')
		.replace(/&nbsp;/g, ' ')
		.replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
		.replace(/\s+/g, ' ')
		.trim();
}

export function searchUrl(query) {
	const q = String(query || '').trim().slice(0, 200);
	if (!q) return '';
	return `https://lite.duckduckgo.com/lite/?q=${encodeURIComponent(q)}`;
}

export function hostOf(raw) {
	try {
		const host = new URL(String(raw || '')).hostname.toLowerCase().replace(/^www\./, '');
		return host || '';
	} catch {
		return '';
	}
}

export function hostsFromHits(hits) {
	const seen = [];
	for (const hit of hits || []) {
		const host = hostOf(hit?.url);
		if (host && !seen.includes(host)) seen.push(host);
	}
	return seen;
}

function queryLabel(call) {
	let args = {};
	try {
		const parsed = JSON.parse(call?.arguments || '{}');
		if (parsed && typeof parsed === 'object') args = parsed;
	} catch {
		args = {};
	}
	if (call?.name === 'web_search') return String(args.query || '').trim().slice(0, 80);
	if (call?.name === 'web_fetch') return hostOf(args.url);
	return '';
}

export function searchActivity(calls, sites = [], failed = false) {
	const looking = (calls || []).filter((call) => call?.name === 'web_search' || call?.name === 'web_fetch');
	if (!looking.length) return [];
	if (failed && !sites.length) return ['Search failed'];
	const heading = looking.some((call) => call.name === 'web_search') ? 'Searching' : 'Reading';
	const queries = [];
	for (const call of looking) {
		const label = queryLabel(call);
		if (label && !queries.includes(label)) queries.push(label);
		if (queries.length >= 4) break;
	}
	const siteLines = [];
	for (const site of sites) {
		const line = `· ${site}`;
		if (site && !siteLines.includes(line)) siteLines.push(line);
		if (siteLines.length >= 8) break;
	}
	return [heading, ...queries, ...siteLines, ...(failed ? ['Search failed'] : [])];
}

export function looksLikeErrorPage(html) {
	const lower = String(html || '').toLowerCase();
	if (lower.includes('result-link') || lower.includes('result__a')) return false;
	if (lower.includes('captcha') || lower.includes('access denied')) return true;
	return String(html || '').length > 8000 && (lower.includes('<html') || lower.includes('<!doctype'));
}

export function performTool(name, argsJson, get) {
	let args = {};
	try {
		const parsed = JSON.parse(argsJson || '{}');
		if (parsed && typeof parsed === 'object') args = parsed;
	} catch {
		return { text: 'Invalid tool arguments.', sites: [], failed: true };
	}
	if (name === 'web_search') {
		const url = searchUrl(args.query);
		if (!url) return { text: 'Need a query.', sites: [], failed: true };
		const html = get(url);
		if (!html) return { text: 'Search failed.', sites: [], failed: true };
		if (looksLikeErrorPage(html)) return { text: 'Search failed.', sites: [], failed: true };
		const hits = parseDuckDuckGo(html);
		return { text: formatHits(hits), sites: hostsFromHits(hits), failed: false };
	}
	if (name === 'web_fetch') {
		const page = publicPageUrl(args.url);
		const host = hostOf(page);
		return { text: page ? 'Page could not be read.' : 'Only public HTTPS pages.', sites: host ? [host] : [], failed: true };
	}
	if (name === 'calculate') return { text: calculate(args.expression), sites: [], failed: false };
	return { text: 'Unknown tool.', sites: [], failed: true };
}

export function toolStep(calls, round, tools, maxRounds = 2) {
	if (!calls?.length || !tools) return 'answer';
	if (round >= maxRounds) return 'finish';
	return 'run';
}

export function parseDuckDuckGo(html) {
	const raw = String(html || '');
	const hits = [];
	const re = /<a\b([^>]*class=['"][^'"]*(?:result__a|result-link)[^'"]*['"][^>]*)>([\s\S]*?)<\/a>/gi;
	let match;
	while ((match = re.exec(raw)) && hits.length < 5) {
		const href = /href=['"]([^'"]+)['"]/i.exec(match[1])?.[1] || '';
		const url = publicPageUrl(unwrapDdg(href));
		if (!url) continue;
		const after = raw.slice(match.index + match[0].length, match.index + match[0].length + 800);
		const snippet = /<(?:a|td)\b[^>]*class=['"][^'"]*result[_-]snippet[^'"]*['"][^>]*>([\s\S]*?)<\/(?:a|td)>/i.exec(after);
		hits.push({
			title: decodeEntities(match[2]).slice(0, 160) || url,
			url,
			snippet: snippet ? decodeEntities(snippet[1]).slice(0, 240) : '',
		});
	}
	return hits;
}

export function formatHits(hits) {
	if (!hits?.length) return 'No results.';
	return hits
		.map((hit, index) => {
			const lines = [`${index + 1}. ${hit.title}`, `   ${hit.url}`];
			if (hit.snippet) lines.push(`   ${hit.snippet}`);
			return lines.join('\n');
		})
		.join('\n');
}

export function stripHtml(html) {
	return String(html || '')
		.replace(/<script[\s\S]*?<\/script>/gi, ' ')
		.replace(/<style[\s\S]*?<\/style>/gi, ' ')
		.replace(/<[^>]+>/g, ' ')
		.replace(/&amp;/g, '&')
		.replace(/&nbsp;/g, ' ')
		.replace(/&quot;/g, '"')
		.replace(/&#39;|&apos;/g, "'")
		.replace(/&lt;/g, '<')
		.replace(/&gt;/g, '>')
		.replace(/\s+/g, ' ')
		.trim();
}

export function calculate(raw) {
	const expr = String(raw || '').trim();
	if (!expr || expr.length > 200 || !/^[\d\s.+\-*/%()a-z,]+$/i.test(expr)) return 'Could not calculate.';
	try {
		const parser = new Calc(expr);
		const value = parser.parse();
		if (!parser.done() || !Number.isFinite(value)) return 'Could not calculate.';
		if (Number.isInteger(value) && Math.abs(value) < 1e15) return String(value);
		return String(Math.round(value * 1e10) / 1e10);
	} catch {
		return 'Could not calculate.';
	}
}

class Calc {
	constructor(src) {
		this.src = src;
		this.i = 0;
	}

	done() {
		this.skip();
		return this.i >= this.src.length;
	}

	skip() {
		while (this.src[this.i] === ' ') this.i += 1;
	}

	parse() {
		const value = this.expr();
		this.skip();
		return value;
	}

	expr() {
		let value = this.term();
		for (;;) {
			this.skip();
			const op = this.src[this.i];
			if (op !== '+' && op !== '-') break;
			this.i += 1;
			const right = this.term();
			value = op === '+' ? value + right : value - right;
		}
		return value;
	}

	term() {
		let value = this.unary();
		for (;;) {
			this.skip();
			const op = this.src[this.i];
			if (op !== '*' && op !== '/' && op !== '%') break;
			this.i += 1;
			const right = this.unary();
			if ((op === '/' || op === '%') && right === 0) throw new Error('div0');
			value = op === '*' ? value * right : op === '/' ? value / right : value % right;
		}
		return value;
	}

	unary() {
		this.skip();
		if (this.src[this.i] === '+') {
			this.i += 1;
			return this.unary();
		}
		if (this.src[this.i] === '-') {
			this.i += 1;
			return -this.unary();
		}
		return this.primary();
	}

	primary() {
		this.skip();
		if (this.src[this.i] === '(') {
			this.i += 1;
			const value = this.expr();
			this.skip();
			if (this.src[this.i] !== ')') throw new Error('paren');
			this.i += 1;
			return value;
		}
		if (/[a-z]/i.test(this.src[this.i] || '')) return this.call();
		return this.number();
	}

	call() {
		const start = this.i;
		while (/[a-z]/i.test(this.src[this.i] || '')) this.i += 1;
		const name = this.src.slice(start, this.i).toLowerCase();
		this.skip();
		if (this.src[this.i] !== '(') throw new Error('call');
		this.i += 1;
		const arg = this.expr();
		this.skip();
		if (this.src[this.i] !== ')') throw new Error('call');
		this.i += 1;
		if (name === 'sqrt') {
			if (arg < 0) throw new Error('sqrt');
			return Math.sqrt(arg);
		}
		if (name === 'abs') return Math.abs(arg);
		throw new Error('fn');
	}

	number() {
		const start = this.i;
		if (this.src[this.i] === '.') this.i += 1;
		while (/[0-9]/.test(this.src[this.i] || '')) this.i += 1;
		if (this.src[this.i] === '.') {
			this.i += 1;
			while (/[0-9]/.test(this.src[this.i] || '')) this.i += 1;
		}
		if (this.i === start) throw new Error('num');
		const value = Number(this.src.slice(start, this.i));
		if (!Number.isFinite(value)) throw new Error('num');
		return value;
	}
}

export const SYSTEM =
	"You are a concise assistant on a builder's phone. Prefer short answers they can act on. Use markdown when it helps: headings, lists, tables, and fenced code. Skip preamble.";

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
	const snippet = String(raw || '').replace(/\s+/g, ' ').slice(0, 280);
	return `LLM error ${code}: ${snippet}`.trim();
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

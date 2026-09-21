export const OVERWRITE_WARNING =
	'This will overwrite any local data on this device (todos, notes, chats, pins, stocks, podcasts, alarms, and settings). OAuth tokens stay here. Continue?';

/** Vault and auth calls. Longer than RSS/Yahoo so a large snapshot can finish. */
export const API_TIMEOUT_MS = 20_000;

export function confirmOverwriteLocal(ask = typeof window !== 'undefined' ? window.confirm.bind(window) : () => false) {
	return Boolean(ask(OVERWRITE_WARNING));
}

export function isAbortError(err) {
	const name = err?.name || '';
	return name === 'AbortError' || name === 'TimeoutError';
}

export async function apiJson(path, { method = 'GET', body, token } = {}) {
	const headers = { accept: 'application/json' };
	if (body !== undefined) headers['content-type'] = 'application/json';
	if (token) headers.authorization = `Bearer ${token}`;
	let res;
	try {
		res = await fetch(path, {
			method,
			headers,
			body: body !== undefined ? JSON.stringify(body) : undefined,
			credentials: 'include',
			cache: 'no-store',
			signal: AbortSignal.timeout(API_TIMEOUT_MS),
		});
	} catch (err) {
		if (isAbortError(err)) throw new Error('Request timed out. Try again.');
		throw err;
	}
	const text = await res.text();
	let data = null;
	try {
		data = text ? JSON.parse(text) : null;
	} catch {
		throw new Error(text.slice(0, 180) || `HTTP ${res.status}`);
	}
	if (!res.ok) throw new Error(data?.error || `HTTP ${res.status}`);
	return data;
}

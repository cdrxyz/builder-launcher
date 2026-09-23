export const OVERWRITE_WARNING =
	'This will overwrite any local data on this device (todos, notes, chats, pins, stocks, podcasts, alarms, and settings). OAuth tokens on this phone stay unless the snapshot includes them. Continue?';

/** Vault and auth calls. Longer than RSS/Yahoo so a large snapshot can finish. */
export const API_TIMEOUT_MS = 20_000;

export function confirmOverwriteLocal(ask = typeof window !== 'undefined' ? window.confirm.bind(window) : () => false) {
	return Boolean(ask(OVERWRITE_WARNING));
}

export function isAbortError(err) {
	const name = err?.name || '';
	return name === 'AbortError' || name === 'TimeoutError';
}

/** Login fields must look like a real account form so iCloud/Bitwarden/Google offer a fill. */
export function fieldAutocomplete(name, type, intent = 'login') {
	if (name === 'email' || type === 'email') return 'username';
	if (name === 'password') return intent === 'signup' ? 'new-password' : 'current-password';
	return 'off';
}

export function hideFromPasswordManagers(name) {
	return name === 'secretKey' || name === 'encryptionKey';
}

export function applyPasswordManagerAttrs(input, name, type, intent = 'login') {
	input.autocomplete = fieldAutocomplete(name, type, intent);
	if (name === 'email') input.id = 'builder-account-email';
	if (name === 'password') input.id = 'builder-account-password';
	if (hideFromPasswordManagers(name)) {
		input.setAttribute('data-lpignore', 'true');
		input.setAttribute('data-1p-ignore', 'true');
		input.setAttribute('data-bwignore', 'true');
	}
}

export function passwordCredentialData(email, password) {
	const id = String(email || '').trim();
	const secret = String(password || '');
	if (!id || !secret) return null;
	return { id, name: id, password: secret };
}

export function fillAccountForm(form, cred) {
	if (!form || !cred) return false;
	const email = form.querySelector('[name="email"]');
	const password = form.querySelector('[name="password"]');
	if (!email || !password) return false;
	if (cred.email) email.value = cred.email;
	if (cred.password) password.value = cred.password;
	return true;
}

export async function storeAccountCredential(
	email,
	password,
	api = globalThis.navigator?.credentials,
	Credential = globalThis.PasswordCredential,
) {
	const data = passwordCredentialData(email, password);
	if (!data || typeof api?.store !== 'function') return false;
	try {
		await api.store(Credential ? new Credential(data) : data);
		return true;
	} catch {
		return false;
	}
}

export async function requestAccountCredential(
	api = globalThis.navigator?.credentials,
	mediation = 'optional',
) {
	if (typeof api?.get !== 'function') return null;
	try {
		const cred = await api.get({ password: true, mediation });
		if (!cred || (cred.type && cred.type !== 'password')) return null;
		const email = String(cred.id || '').trim();
		const secret = String(cred.password || '');
		if (!email || !secret) return null;
		return { email, password: secret };
	} catch {
		return null;
	}
}

export async function apiJson(path, { method = 'GET', body, token, timeout = API_TIMEOUT_MS } = {}) {
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
			signal: AbortSignal.timeout(timeout),
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

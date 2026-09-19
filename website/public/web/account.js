export async function apiJson(path, { method = 'GET', body, token } = {}) {
	const headers = { accept: 'application/json' };
	if (body !== undefined) headers['content-type'] = 'application/json';
	if (token) headers.authorization = `Bearer ${token}`;
	const res = await fetch(path, {
		method,
		headers,
		body: body !== undefined ? JSON.stringify(body) : undefined,
		credentials: 'include',
		cache: 'no-store',
	});
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

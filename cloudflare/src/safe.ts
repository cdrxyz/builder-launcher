const PRIVATE_HOST =
	/^(localhost|127\.0\.0\.1|0\.0\.0\.0|\[::1\]|10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[0-1])\.\d+\.\d+|169\.254\.\d+\.\d+|.*\.local|metadata\.google\.internal)$/i;

export const MAX_S3_BYTES = 4_000_000;
export const MAX_FEED_BYTES = 2_000_000;
export const USER_AGENT = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 BuilderLauncher';

export function isSafeHttpsUrl(raw: string): URL | null {
	let url: URL;
	try {
		url = new URL(raw.trim());
	} catch {
		return null;
	}
	if (url.protocol !== 'https:') return null;
	if (url.username || url.password) return null;
	const host = url.hostname.toLowerCase();
	if (PRIVATE_HOST.test(host)) return null;
	if (host.includes(':')) return null;
	return url;
}

export function isYahooHost(host: string): boolean {
	return host === 'query1.finance.yahoo.com' || host === 'query2.finance.yahoo.com';
}

export function isItunesHost(host: string): boolean {
	return host === 'itunes.apple.com';
}

export function s3EndpointOk(endpoint: string): boolean {
	const url = isSafeHttpsUrl(endpoint);
	return Boolean(url);
}

export function jsonError(message: string, status = 400): Response {
	return new Response(JSON.stringify({ error: message }), {
		status,
		headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
	});
}

export function textError(message: string, status = 400): Response {
	return new Response(message, {
		status,
		headers: { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store' },
	});
}

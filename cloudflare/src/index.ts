import { objectUrl, regionFor, sign } from '../../website/public/web/s3.js';
import {
	isItunesHost,
	isSafeHttpsUrl,
	isYahooHost,
	jsonError,
	MAX_FEED_BYTES,
	MAX_S3_BYTES,
	s3EndpointOk,
	textError,
	USER_AGENT,
} from './safe';

export interface Env {
	ASSETS: { fetch: (request: Request) => Promise<Response> };
}

const STATIC = /\.(css|js|mjs|map|png|jpe?g|gif|svg|ico|webp|woff2?|ttf|webmanifest)$/i;

export default {
	async fetch(request: Request, env: Env): Promise<Response> {
		const url = new URL(request.url);
		if (url.pathname === '/api/up') {
			return Response.json({ ok: true, service: 'builder-launcher' });
		}
		if (url.pathname.startsWith('/api/')) {
			if (!sameOrigin(request)) return jsonError('Origin not allowed', 403);
			return handleApi(request, url);
		}
		if (STATIC.test(url.pathname) || url.pathname === '/sw.js' || url.pathname === '/manifest.webmanifest') {
			const asset = await env.ASSETS.fetch(request);
			if (asset.status !== 404) return withPwaHeaders(asset);
		}
		if (url.pathname === '/' || url.pathname === '/index.html') {
			const index = await env.ASSETS.fetch(new Request(new URL('/index.html', request.url)));
			return withPwaHeaders(index);
		}
		const asset = await env.ASSETS.fetch(request);
		if (asset.status !== 404) return withPwaHeaders(asset);
		const index = await env.ASSETS.fetch(new Request(new URL('/index.html', request.url)));
		return withPwaHeaders(index);
	},
};

async function handleApi(request: Request, url: URL): Promise<Response> {
	try {
		if (url.pathname === '/api/s3/get' && request.method === 'POST') return s3Get(request);
		if (url.pathname === '/api/s3/put' && request.method === 'POST') return s3Put(request);
		if (url.pathname === '/api/yahoo/search' && request.method === 'GET') {
			return yahooSearch(url.searchParams.get('q') || '');
		}
		if (url.pathname === '/api/yahoo/chart' && request.method === 'GET') {
			return yahooChart(url.searchParams.get('symbol') || '');
		}
		if (url.pathname === '/api/podcasts/search' && request.method === 'GET') {
			return itunesSearch(url.searchParams.get('q') || '');
		}
		if (url.pathname === '/api/feed' && request.method === 'GET') {
			return fetchFeed(url.searchParams.get('url') || '');
		}
		return jsonError('Not found', 404);
	} catch (err) {
		const message = err instanceof Error ? err.message : 'Proxy failed';
		return jsonError(message, 502);
	}
}

type Creds = { endpoint: string; bucket: string; accessKey: string; secretKey: string };

async function readCreds(request: Request): Promise<Creds> {
	const body = (await request.json()) as Record<string, unknown>;
	const creds = {
		endpoint: String(body.endpoint || '').trim(),
		bucket: String(body.bucket || '').trim(),
		accessKey: String(body.accessKey || '').trim(),
		secretKey: String(body.secretKey || '').trim(),
	};
	if (!creds.endpoint || !creds.bucket || !creds.accessKey || !creds.secretKey) {
		throw new Error('endpoint, bucket, access key, and secret key are required');
	}
	if (!s3EndpointOk(creds.endpoint)) throw new Error('S3 endpoint must be https on a public host');
	return creds;
}

async function s3Get(request: Request): Promise<Response> {
	const creds = await readCreds(request);
	const bytes = await s3Fetch('GET', creds, new Uint8Array(0));
	return new Response(bytes, {
		headers: { 'content-type': 'application/octet-stream', 'cache-control': 'no-store' },
	});
}

async function s3Put(request: Request): Promise<Response> {
	const body = (await request.json()) as Record<string, unknown>;
	const creds = {
		endpoint: String(body.endpoint || '').trim(),
		bucket: String(body.bucket || '').trim(),
		accessKey: String(body.accessKey || '').trim(),
		secretKey: String(body.secretKey || '').trim(),
	};
	if (!s3EndpointOk(creds.endpoint)) throw new Error('S3 endpoint must be https on a public host');
	const b64 = String(body.bodyB64 || '');
	if (!b64) throw new Error('bodyB64 is required');
	const raw = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
	if (raw.length > MAX_S3_BYTES) throw new Error('Backup too large');
	await s3Fetch('PUT', creds, raw, { 'content-type': 'application/octet-stream' });
	return Response.json({ ok: true });
}

async function s3Fetch(
	method: string,
	creds: Creds,
	payload: Uint8Array,
	extraHeaders: Record<string, string> = {},
): Promise<Uint8Array> {
	const url = objectUrl(creds.endpoint, creds.bucket);
	const now = new Date();
	const p = (n: number) => String(n).padStart(2, '0');
	const amzDate = `${now.getUTCFullYear()}${p(now.getUTCMonth() + 1)}${p(now.getUTCDate())}T${p(now.getUTCHours())}${p(now.getUTCMinutes())}${p(now.getUTCSeconds())}Z`;
	const signed = await sign({
		method,
		url,
		accessKey: creds.accessKey,
		secretKey: creds.secretKey,
		region: regionFor(creds.endpoint),
		payload,
		amzDate,
		extraHeaders,
	});
	const headers: Record<string, string> = {};
	for (const [k, v] of Object.entries(signed)) {
		if (k === 'host') continue;
		headers[k === 'authorization' ? 'Authorization' : k === 'content-type' ? 'Content-Type' : k] = v;
	}
	const res = await fetch(url, {
		method,
		headers,
		body: method === 'GET' ? undefined : payload,
	});
	const bytes = new Uint8Array(await res.arrayBuffer());
	if (bytes.length > MAX_S3_BYTES) throw new Error('Backup too large');
	if (!res.ok) {
		const snippet = new TextDecoder().decode(bytes).replace(/\s+/g, ' ').slice(0, 180);
		if (res.status === 403) throw new Error('S3 access denied');
		if (res.status === 404) throw new Error(method === 'PUT' ? 'S3 bucket not found' : 'No backup in that bucket');
		throw new Error(`S3 HTTP ${res.status}${snippet ? `: ${snippet}` : ''}`);
	}
	return bytes;
}

async function yahooSearch(q: string): Promise<Response> {
	const query = q.trim().slice(0, 80);
	if (!query) return jsonError('q is required');
	const target = `https://query1.finance.yahoo.com/v1/finance/search?q=${encodeURIComponent(query)}&quotesCount=8&newsCount=0&listsCount=0`;
	return proxyJson(target, 'query1.finance.yahoo.com');
}

async function yahooChart(symbol: string): Promise<Response> {
	const s = symbol.trim().toUpperCase().slice(0, 12);
	if (!s) return jsonError('symbol is required');
	const target = `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(s)}?interval=1d&range=1d`;
	return proxyJson(target, 'query1.finance.yahoo.com');
}

async function itunesSearch(q: string): Promise<Response> {
	const query = q.trim().slice(0, 80);
	if (!query) return jsonError('q is required');
	const target = `https://itunes.apple.com/search?term=${encodeURIComponent(query)}&media=podcast&entity=podcast&limit=8`;
	return proxyJson(target, 'itunes.apple.com');
}

async function proxyJson(target: string, expectedHost: string): Promise<Response> {
	const url = isSafeHttpsUrl(target);
	if (!url) return jsonError('Bad URL');
	if (url.hostname !== expectedHost) return jsonError('Host not allowed');
	if (!isYahooHost(url.hostname) && !isItunesHost(url.hostname)) return jsonError('Host not allowed');
	const res = await fetch(url.toString(), { headers: { 'user-agent': USER_AGENT, accept: 'application/json' } });
	const text = await res.text();
	return new Response(text, {
		status: res.ok ? 200 : res.status,
		headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
	});
}

async function fetchFeed(raw: string): Promise<Response> {
	const url = isSafeHttpsUrl(raw);
	if (!url) return textError('Feed URL must be https on a public host');
	const res = await fetch(url.toString(), { headers: { 'user-agent': USER_AGENT, accept: 'application/rss+xml, application/xml, text/xml, */*' } });
	const buf = await res.arrayBuffer();
	if (buf.byteLength > MAX_FEED_BYTES) return textError('Feed too large', 413);
	if (!res.ok) return textError(`Feed HTTP ${res.status}`, 502);
	return new Response(buf, {
		headers: { 'content-type': 'application/xml; charset=utf-8', 'cache-control': 'no-store' },
	});
}

function sameOrigin(request: Request): boolean {
	const origin = request.headers.get('Origin');
	if (!origin) return request.method === 'GET';
	try {
		return new URL(origin).origin === new URL(request.url).origin;
	} catch {
		return false;
	}
}

function withPwaHeaders(res: Response): Response {
	const headers = new Headers(res.headers);
	headers.set('cache-control', 'no-cache');
	return new Response(res.body, { status: res.status, headers });
}

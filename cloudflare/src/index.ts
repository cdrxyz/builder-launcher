import { objectUrl, regionFor, sign } from '../../website/public/web/s3.js';
import { handleAuth } from './auth';
import {
	isItunesHost,
	isFyydHost,
	isOpenMeteoHost,
	isSafeHttpsUrl,
	isYahooHost,
	jsonError,
	MAX_FEED_BYTES,
	MAX_S3_BYTES,
	UPSTREAM_TIMEOUT_MS,
	s3EndpointOk,
	textError,
	USER_AGENT,
} from './safe';
import { fyydToItunes } from './podcasts';
import { handleVault } from './vault';

export interface Env {
	ASSETS: { fetch: (request: Request) => Promise<Response> };
	DB?: D1Database;
	VAULT?: R2Bucket;
}

const STATIC = /\.(css|js|mjs|map|png|jpe?g|gif|svg|ico|webp|woff2?|ttf|webmanifest)$/i;

export default {
	async fetch(request: Request, env: Env): Promise<Response> {
		const url = new URL(request.url);
		if (url.pathname === '/api/up') {
			return Response.json({ ok: true, service: 'builder-launcher' });
		}
		if (url.pathname.startsWith('/api/')) {
			if (!apiAllowed(request)) return jsonError('Origin not allowed', 403);
			return handleApi(request, url, env);
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

async function handleApi(request: Request, url: URL, env: Env): Promise<Response> {
	try {
		if (url.pathname.startsWith('/api/auth/') || url.pathname === '/api/vault') {
			if (!env.DB) return jsonError('Account sync is not configured on this Worker', 503);
			const auth = await handleAuth(request, url, { DB: env.DB });
			if (auth) return auth;
			const vault = await handleVault(request, url, { DB: env.DB, VAULT: env.VAULT });
			if (vault) return vault;
		}
		if (url.pathname === '/api/s3/get' && request.method === 'POST') return s3Get(request);
		if (url.pathname === '/api/s3/put' && request.method === 'POST') return s3Put(request);
		if (url.pathname === '/api/yahoo/search' && request.method === 'GET') {
			return yahooSearch(url.searchParams.get('q') || '');
		}
		if (url.pathname === '/api/yahoo/chart' && request.method === 'GET') {
			return yahooChart(
				url.searchParams.get('symbol') || '',
				url.searchParams.get('range') || '1d',
				url.searchParams.get('interval') || '1d',
			);
		}
		if (url.pathname === '/api/yahoo/timeseries' && request.method === 'GET') {
			return yahooTimeseries(url.searchParams.get('symbol') || '');
		}
		if (url.pathname === '/api/podcasts/search' && request.method === 'GET') {
			return itunesSearch(url.searchParams.get('q') || '');
		}
		if (url.pathname === '/api/feed' && request.method === 'GET') {
			return fetchFeed(url.searchParams.get('url') || '');
		}
		if (url.pathname === '/api/weather/search' && request.method === 'GET') {
			return weatherSearch(url.searchParams.get('q') || '');
		}
		if (url.pathname === '/api/weather/forecast' && request.method === 'GET') {
			return weatherForecast(url.searchParams.get('lat') || '', url.searchParams.get('lon') || '');
		}
		if (url.pathname === '/api/weather/air' && request.method === 'GET') {
			return weatherAir(url.searchParams.get('lat') || '', url.searchParams.get('lon') || '');
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

async function yahooChart(symbol: string, rangeRaw: string, intervalRaw: string): Promise<Response> {
	const s = symbol.trim().toUpperCase().slice(0, 12);
	if (!s) return jsonError('symbol is required');
	const range = ['1d', '5d', '1mo', '3mo', '1y', '5y', '10y'].includes(rangeRaw) ? rangeRaw : '1d';
	const interval = ['5m', '15m', '1d', '1wk'].includes(intervalRaw) ? intervalRaw : '1d';
	const target = `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(s)}?interval=${interval}&range=${range}&includePrePost=true`;
	return proxyJson(target, 'query1.finance.yahoo.com');
}

async function yahooTimeseries(symbol: string): Promise<Response> {
	const s = symbol.trim().toUpperCase().slice(0, 12);
	if (!s) return jsonError('symbol is required');
	const now = Math.floor(Date.now() / 1000);
	const start = now - 400 * 86_400;
	const types = 'trailingPeRatio,trailingMarketCap,trailingDividendYield,trailingDilutedEPS';
	const target = `https://query1.finance.yahoo.com/ws/fundamentals-timeseries/v1/finance/timeseries/${encodeURIComponent(s)}?symbol=${encodeURIComponent(s)}&type=${types}&period1=${start}&period2=${now}`;
	return proxyJson(target, 'query1.finance.yahoo.com');
}

async function itunesSearch(q: string): Promise<Response> {
	const query = q.trim().slice(0, 80);
	if (!query) return jsonError('q is required');
	// Cloudflare rate-limits Worker subrequests to itunes.apple.com (429
	// "itunes-apple-com|general|<cf-ip>"). The PWA hits Apple from the
	// browser (CORS *). This endpoint uses fyyd so search still works
	// when Apple is blocked or the client cannot call itunes directly.
	const target = `https://api.fyyd.de/0.2/search/podcast?term=${encodeURIComponent(query)}&count=8`;
	const url = isSafeHttpsUrl(target);
	if (!url || !isFyydHost(url.hostname)) return jsonError('Host not allowed');
	const res = await fetch(url.toString(), {
		headers: { 'user-agent': USER_AGENT, accept: 'application/json' },
		signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
	});
	const text = await res.text();
	if (!res.ok) return jsonError('Podcast search failed', 502);
	return new Response(fyydToItunes(text), {
		headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
	});
}

async function proxyJson(target: string, expectedHost: string): Promise<Response> {
	const url = isSafeHttpsUrl(target);
	if (!url) return jsonError('Bad URL');
	if (url.hostname !== expectedHost) return jsonError('Host not allowed');
	if (!isYahooHost(url.hostname) && !isItunesHost(url.hostname)) return jsonError('Host not allowed');
	const res = await fetch(url.toString(), {
		headers: { 'user-agent': USER_AGENT, accept: 'application/json' },
		signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
	});
	const text = await res.text();
	return new Response(text, {
		status: res.ok ? 200 : res.status,
		headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
	});
}

async function fetchFeed(raw: string): Promise<Response> {
	const url = isSafeHttpsUrl(raw);
	if (!url) return textError('Feed URL must be https on a public host');
	try {
		const res = await fetch(url.toString(), {
			headers: { 'user-agent': USER_AGENT, accept: 'application/rss+xml, application/xml, text/xml, */*' },
			signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
		});
		const buf = await res.arrayBuffer();
		if (buf.byteLength > MAX_FEED_BYTES) return textError('Feed too large', 413);
		if (!res.ok) return textError(`Feed HTTP ${res.status}`, 502);
		return new Response(buf, {
			headers: { 'content-type': 'application/xml; charset=utf-8', 'cache-control': 'no-store' },
		});
	} catch {
		return textError('Feed timed out', 504);
	}
}

async function weatherSearch(q: string): Promise<Response> {
	const query = q.trim().slice(0, 80);
	if (query.length < 2) return jsonError('q is required');
	const target = `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(query)}&count=6&language=en&format=json`;
	return proxyOpenMeteo(target);
}

async function weatherForecast(latRaw: string, lonRaw: string): Promise<Response> {
	const coords = weatherCoords(latRaw, lonRaw);
	if (!coords) return jsonError('lat and lon are required');
	const target =
		`https://api.open-meteo.com/v1/forecast?latitude=${encodeURIComponent(String(coords.lat))}` +
		`&longitude=${encodeURIComponent(String(coords.lon))}` +
		'&current=temperature_2m,apparent_temperature,weather_code,relative_humidity_2m,precipitation,wind_speed_10m,wind_direction_10m,wind_gusts_10m,surface_pressure,visibility,cloud_cover,is_day,dew_point_2m' +
		'&hourly=temperature_2m,weather_code,precipitation_probability,uv_index,is_day' +
		'&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,sunrise,sunset,uv_index_max' +
		'&forecast_days=7&timezone=auto&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm';
	return proxyOpenMeteo(target);
}

async function weatherAir(latRaw: string, lonRaw: string): Promise<Response> {
	const coords = weatherCoords(latRaw, lonRaw);
	if (!coords) return jsonError('lat and lon are required');
	const target =
		`https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${encodeURIComponent(String(coords.lat))}` +
		`&longitude=${encodeURIComponent(String(coords.lon))}` +
		'&current=us_aqi,european_aqi';
	return proxyOpenMeteo(target);
}

function weatherCoords(latRaw: string, lonRaw: string): { lat: number; lon: number } | null {
	const lat = Number(latRaw);
	const lon = Number(lonRaw);
	if (!Number.isFinite(lat) || !Number.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
	return { lat, lon };
}

async function proxyOpenMeteo(target: string): Promise<Response> {
	const url = isSafeHttpsUrl(target);
	if (!url || !isOpenMeteoHost(url.hostname)) return jsonError('Host not allowed');
	const res = await fetch(url.toString(), {
		headers: { 'user-agent': USER_AGENT, accept: 'application/json' },
		signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
	});
	const text = await res.text();
	return new Response(text, {
		status: res.ok ? 200 : res.status,
		headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
	});
}

function apiAllowed(request: Request): boolean {
	const origin = request.headers.get('Origin');
	if (!origin) return true;
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

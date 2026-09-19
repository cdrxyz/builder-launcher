/** Path-style SigV4, matching S3Signer.kt */

export const OBJECT_KEY = 'builder-launcher/backup.enc';
const UNRESERVED = new Set('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~');
const text = new TextEncoder();

export function regionFor(endpoint) {
	const host = hostOf(endpoint);
	if (host.endsWith('r2.cloudflarestorage.com')) return 'auto';
	if (host === 's3.amazonaws.com' || host === 's3.dualstack.amazonaws.com') return 'us-east-1';
	if (host.startsWith('s3.')) return host.split('.')[1] || 'us-east-1';
	return 'us-east-1';
}

export function objectUrl(endpoint, bucket, key = OBJECT_KEY) {
	const base = endpoint.trim().replace(/\/+$/, '');
	return `${base}/${bucket.trim().replace(/^\/+|\/+$/g, '')}/${key.replace(/^\/+/, '')}`;
}

export function credentialsReady(endpoint, bucket, accessKey, secretKey) {
	return Boolean(
		endpoint &&
			bucket &&
			accessKey &&
			secretKey &&
			(endpoint.startsWith('https://') || endpoint.startsWith('http://')),
	);
}

export function ready(endpoint, bucket, accessKey, secretKey, encryptionKey) {
	return credentialsReady(endpoint, bucket, accessKey, secretKey) && Boolean(encryptionKey);
}

export async function sign({
	method,
	url,
	accessKey,
	secretKey,
	region,
	payload,
	amzDate,
	extraHeaders = {},
}) {
	const host = hostOf(url);
	const uri = pathOf(url);
	const payloadHash = await sha256Hex(payload);
	const headers = {
		host,
		'x-amz-content-sha256': payloadHash,
		'x-amz-date': amzDate,
	};
	for (const [k, v] of Object.entries(extraHeaders)) headers[k.toLowerCase()] = v;
	const signedNames = Object.keys(headers).sort();
	const canonicalHeaders = signedNames.map((name) => `${name}:${headers[name].trim()}\n`).join('');
	const signedHeaderList = signedNames.join(';');
	const canonical = [
		method.toUpperCase(),
		uri,
		'',
		canonicalHeaders,
		signedHeaderList,
		payloadHash,
	].join('\n');
	const dateStamp = amzDate.slice(0, 8);
	const scope = `${dateStamp}/${region}/s3/aws4_request`;
	const stringToSign = [
		'AWS4-HMAC-SHA256',
		amzDate,
		scope,
		await sha256Hex(text.encode(canonical)),
	].join('\n');
	const signing = await signingKey(secretKey, dateStamp, region);
	const signature = hex(await hmac(signing, stringToSign));
	const authorization = `AWS4-HMAC-SHA256 Credential=${accessKey}/${scope}, SignedHeaders=${signedHeaderList}, Signature=${signature}`;
	return { ...headers, authorization };
}

export async function s3Get(creds, now = new Date()) {
	return s3Fetch('GET', creds, new Uint8Array(0), now);
}

export async function s3Put(creds, body, now = new Date()) {
	return s3Fetch('PUT', creds, body, now, { 'content-type': 'application/octet-stream' });
}

async function s3Fetch(method, creds, payload, now, extraHeaders = {}) {
	const url = objectUrl(creds.endpoint, creds.bucket);
	const amzDate = amzDateUtc(now);
	const region = regionFor(creds.endpoint);
	const signed = await sign({
		method,
		url,
		accessKey: creds.accessKey,
		secretKey: creds.secretKey,
		region,
		payload,
		amzDate,
		extraHeaders,
	});
	const headers = {};
	for (const [k, v] of Object.entries(signed)) {
		if (k === 'host') continue;
		headers[headerName(k)] = v;
	}
	let res;
	try {
		res = await fetch(url, {
			method,
			headers,
			body: method === 'GET' ? undefined : payload,
		});
	} catch {
		throw new Error(corsHint());
	}
	const bytes = new Uint8Array(await res.arrayBuffer());
	if (!res.ok) throw new Error(s3Error(res.status, decodeSnippet(bytes), method === 'PUT'));
	return bytes;
}

export function corsHint() {
	return (
		'The bucket blocked this browser (CORS). On R2: bucket Settings → CORS. Allow origin https://cdrxyz.github.io, methods GET and PUT, headers *.'
	);
}

export function uriEncode(input, encodeSlash) {
	const bytes = text.encode(input);
	let out = '';
	for (const b of bytes) {
		const ch = String.fromCharCode(b);
		if (UNRESERVED.has(ch) || (ch === '/' && !encodeSlash)) out += ch;
		else out += `%${'0123456789ABCDEF'[b >> 4]}${'0123456789ABCDEF'[b & 0x0f]}`;
	}
	return out;
}

export async function sha256Hex(data) {
	const buf = data instanceof Uint8Array ? data : text.encode(String(data));
	return hex(await crypto.subtle.digest('SHA-256', buf));
}

async function signingKey(secret, dateStamp, region) {
	const kDate = await hmac(text.encode(`AWS4${secret}`), dateStamp);
	const kRegion = await hmac(kDate, region);
	const kService = await hmac(kRegion, 's3');
	return hmac(kService, 'aws4_request');
}

async function hmac(key, data) {
	const raw = typeof data === 'string' ? text.encode(data) : data;
	const cryptoKey = await crypto.subtle.importKey('raw', key, { name: 'HMAC', hash: 'SHA-256' }, false, [
		'sign',
	]);
	return new Uint8Array(await crypto.subtle.sign('HMAC', cryptoKey, raw));
}

function hex(buf) {
	return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

function hostOf(url) {
	const noScheme = url.includes('://') ? url.slice(url.indexOf('://') + 3) : url;
	return noScheme.split('/')[0].split('?')[0].toLowerCase();
}

function pathOf(url) {
	const noScheme = url.includes('://') ? url.slice(url.indexOf('://') + 3) : url;
	const slash = noScheme.indexOf('/');
	const path = slash === -1 ? '' : noScheme.slice(slash + 1).split('?')[0];
	if (!path) return '/';
	return `/${path.split('/').map((part) => uriEncode(part, true)).join('/')}`;
}

function headerName(key) {
	if (key === 'authorization') return 'Authorization';
	if (key === 'content-type') return 'Content-Type';
	return key;
}

function amzDateUtc(now) {
	const d = now instanceof Date ? now : new Date(now);
	const p = (n) => String(n).padStart(2, '0');
	return `${d.getUTCFullYear()}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}T${p(d.getUTCHours())}${p(d.getUTCMinutes())}${p(d.getUTCSeconds())}Z`;
}

function decodeSnippet(bytes) {
	try {
		return new TextDecoder().decode(bytes).replace(/\s+/g, ' ').slice(0, 180);
	} catch {
		return '';
	}
}

function s3Error(code, body, upload) {
	if (code === 403) return 'S3 access denied';
	if (code === 404) return upload ? 'S3 bucket not found' : 'No backup in that bucket';
	return `S3 HTTP ${code}${body ? `: ${body}` : ''}`;
}

import {
	DUMMY_HASH,
	hashPassword,
	isValidEmail,
	isValidPassword,
	normalizeEmail,
	randomToken,
	sha256Hex,
	verifyPassword,
} from './crypto';
import { jsonError } from './safe';

export type AuthEnv = { DB: D1Database };

const SESSION_DAYS = 30;
const COOKIE = 'bl_session';

type UserRow = { id: string; email: string; password_hash: string };

export async function handleAuth(request: Request, url: URL, env: AuthEnv): Promise<Response | null> {
	if (url.pathname === '/api/auth/signup' && request.method === 'POST') return signup(request, env);
	if (url.pathname === '/api/auth/login' && request.method === 'POST') return login(request, env);
	if (url.pathname === '/api/auth/logout' && request.method === 'POST') return logout(request, env);
	if (url.pathname === '/api/auth/me' && request.method === 'GET') return me(request, env);
	return null;
}

export async function currentUser(request: Request, env: AuthEnv): Promise<{ id: string; email: string } | null> {
	const token = sessionToken(request);
	if (!token) return null;
	const hash = await sha256Hex(token);
	const row = await env.DB.prepare(
		`SELECT users.id AS id, users.email AS email
		 FROM sessions JOIN users ON users.id = sessions.user_id
		 WHERE sessions.token_hash = ? AND sessions.expires_at > datetime('now')`,
	)
		.bind(hash)
		.first<{ id: string; email: string }>();
	return row || null;
}

async function signup(request: Request, env: AuthEnv): Promise<Response> {
	const limited = await rateLimit(env.DB, `signup:${clientIp(request)}`, 10, 60 * 60);
	if (limited) return limited;
	const body = await readAuthBody(request);
	if (body instanceof Response) return body;
	const existing = await env.DB.prepare('SELECT id FROM users WHERE email = ?').bind(body.email).first();
	if (existing) return jsonError('An account with that email already exists', 409);
	const id = randomToken(16);
	const passwordHash = await hashPassword(body.password);
	try {
		await env.DB.prepare('INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)')
			.bind(id, body.email, passwordHash)
			.run();
	} catch {
		return jsonError('An account with that email already exists', 409);
	}
	await writeAudit(env.DB, { actorId: id, action: 'auth.signup', ip: clientIp(request) });
	return issueSession(env, { id, email: body.email }, request);
}

async function login(request: Request, env: AuthEnv): Promise<Response> {
	const limited = await rateLimit(env.DB, `login:${clientIp(request)}`, 20, 15 * 60);
	if (limited) return limited;
	const body = await readAuthBody(request);
	if (body instanceof Response) return body;
	const user = await env.DB.prepare('SELECT id, email, password_hash FROM users WHERE email = ?')
		.bind(body.email)
		.first<UserRow>();
	const ok = await verifyPassword(body.password, user?.password_hash || DUMMY_HASH);
	if (!user || !ok) {
		await writeAudit(env.DB, { actorId: user?.id || '', action: 'auth.login_failed', ip: clientIp(request) });
		return jsonError('Email or password is wrong', 401);
	}
	await writeAudit(env.DB, { actorId: user.id, action: 'auth.login', ip: clientIp(request) });
	return issueSession(env, user, request);
}

async function logout(request: Request, env: AuthEnv): Promise<Response> {
	const token = sessionToken(request);
	if (token) {
		const hash = await sha256Hex(token);
		await env.DB.prepare('DELETE FROM sessions WHERE token_hash = ?').bind(hash).run();
	}
	const res = jsonOk({ ok: true });
	res.headers.append('Set-Cookie', clearCookie(request));
	return res;
}

async function me(request: Request, env: AuthEnv): Promise<Response> {
	const user = await currentUser(request, env);
	if (!user) return jsonError('Not signed in', 401);
	return jsonOk({ ok: true, email: user.email });
}

async function issueSession(env: AuthEnv, user: { id: string; email: string }, request: Request): Promise<Response> {
	const token = randomToken(32);
	const hash = await sha256Hex(token);
	await env.DB.prepare('INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, datetime(\'now\', ?))')
		.bind(hash, user.id, `+${SESSION_DAYS} days`)
		.run();
	const res = jsonOk({ ok: true, email: user.email, token });
	res.headers.append('Set-Cookie', sessionCookie(token, request));
	return res;
}

async function readAuthBody(request: Request): Promise<{ email: string; password: string } | Response> {
	let raw: Record<string, unknown>;
	try {
		raw = (await request.json()) as Record<string, unknown>;
	} catch {
		return jsonError('JSON body required');
	}
	const email = normalizeEmail(String(raw.email || ''));
	const password = String(raw.password || '');
	if (!isValidEmail(email)) return jsonError('Enter a valid email');
	if (!isValidPassword(password)) return jsonError('Password must be 8–128 characters');
	return { email, password };
}

export function sessionToken(request: Request): string {
	const auth = request.headers.get('Authorization') || '';
	if (auth.toLowerCase().startsWith('bearer ')) return auth.slice(7).trim();
	const cookie = request.headers.get('Cookie') || '';
	for (const part of cookie.split(';')) {
		const [name, ...rest] = part.trim().split('=');
		if (name === COOKIE) return rest.join('=').trim();
	}
	return '';
}

function sessionCookie(token: string, request: Request): string {
	const secure = new URL(request.url).protocol === 'https:' ? '; Secure' : '';
	return `${COOKIE}=${token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${SESSION_DAYS * 86400}${secure}`;
}

function clearCookie(request: Request): string {
	const secure = new URL(request.url).protocol === 'https:' ? '; Secure' : '';
	return `${COOKIE}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0${secure}`;
}

function jsonOk(body: unknown): Response {
	return new Response(JSON.stringify(body), {
		headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
	});
}

export async function rateLimit(db: D1Database, key: string, max: number, windowSec: number): Promise<Response | null> {
	const row = await db.prepare('SELECT count, window_start FROM rate_limits WHERE key = ?').bind(key).first<{
		count: number;
		window_start: string;
	}>();
	const now = Date.now();
	const start = row ? Date.parse(row.window_start.includes('T') ? row.window_start : `${row.window_start.replace(' ', 'T')}Z`) : 0;
	if (!row || Number.isNaN(start) || now - start > windowSec * 1000) {
		await db
			.prepare('INSERT INTO rate_limits (key, count, window_start) VALUES (?, 1, datetime(\'now\')) ON CONFLICT(key) DO UPDATE SET count = 1, window_start = datetime(\'now\')')
			.bind(key)
			.run();
		return null;
	}
	if (row.count >= max) {
		return jsonError('Too many attempts. Try again later.', 429);
	}
	await db.prepare('UPDATE rate_limits SET count = count + 1 WHERE key = ?').bind(key).run();
	return null;
}

export async function writeAudit(
	db: D1Database,
	row: { actorId: string; action: string; ip: string },
): Promise<void> {
	try {
		await db
			.prepare(
				'INSERT INTO audit_log (id, actor_type, actor_id, action, ip) VALUES (?, ?, ?, ?, ?)',
			)
			.bind(randomToken(12), 'user', row.actorId, row.action, row.ip)
			.run();
	} catch (err) {
		console.error('audit failed', err);
	}
}

export function clientIp(request: Request): string {
	return request.headers.get('CF-Connecting-IP') || request.headers.get('X-Forwarded-For') || '';
}

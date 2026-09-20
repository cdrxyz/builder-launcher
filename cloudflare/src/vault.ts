import { currentUser, writeAudit, clientIp } from './auth';
import { jsonError } from './safe';
import { mergeDocs, slimDoc } from '../../website/public/web/merge.js';

export type VaultEnv = { DB: D1Database; VAULT?: R2Bucket };

/** Uncompressed JSON after dropping podcast show notes. R2 holds the blob. */
export const MAX_DOC = 25_000_000;
/** D1 row cap is 2 MB. Used only when R2 is not bound. */
const MAX_D1_DOC = 1_800_000;

export async function handleVault(request: Request, url: URL, env: VaultEnv): Promise<Response | null> {
	if (url.pathname !== '/api/vault') return null;
	const user = await currentUser(request, env);
	if (!user) return jsonError('Not signed in', 401);
	if (request.method === 'GET') return getVault(env, user.id);
	if (request.method === 'PUT') return putVault(request, env, user.id);
	return jsonError('Method not allowed', 405);
}

async function getVault(env: VaultEnv, userId: string): Promise<Response> {
	const row = await env.DB.prepare('SELECT revision, document, updated_at FROM vaults WHERE user_id = ?')
		.bind(userId)
		.first<{ revision: number; document: string; updated_at: string }>();
	const document = await loadDocument(env, userId, row?.document);
	if (!row && document == null) {
		return json({ ok: true, revision: 0, document: null, updatedAt: null });
	}
	return json({
		ok: true,
		revision: row?.revision || 0,
		document,
		updatedAt: row?.updated_at || null,
	});
}

async function putVault(request: Request, env: VaultEnv, userId: string): Promise<Response> {
	let raw: Record<string, unknown>;
	try {
		raw = (await request.json()) as Record<string, unknown>;
	} catch {
		return jsonError('JSON body required');
	}
	const incoming = raw.document;
	if (!incoming || typeof incoming !== 'object' || Array.isArray(incoming)) {
		return jsonError('document object required');
	}
	const slimmed = slimDoc(incoming);
	const encoded = JSON.stringify(slimmed);
	if (encoded.length > MAX_DOC) return jsonError(tooLarge(encoded.length), 413);
	const existing = await env.DB.prepare('SELECT revision, document FROM vaults WHERE user_id = ?')
		.bind(userId)
		.first<{ revision: number; document: string }>();
	const previous = await loadDocument(env, userId, existing?.document);
	const merged = slimDoc(previous ? mergeDocs(previous, slimmed) : slimmed);
	const next = JSON.stringify(merged);
	if (next.length > MAX_DOC) return jsonError(tooLarge(next.length), 413);
	if (!env.VAULT && next.length > MAX_D1_DOC) return jsonError(tooLarge(next.length), 413);
	const revision = (existing?.revision || 0) + 1;
	await storeDocument(env, userId, revision, next);
	await writeAudit(env.DB, { actorId: userId, action: 'vault.put', ip: clientIp(request) });
	return json({ ok: true, revision, document: merged });
}

async function loadDocument(env: VaultEnv, userId: string, fallback: string | undefined): Promise<unknown | null> {
	if (env.VAULT) {
		const obj = await env.VAULT.get(r2Key(userId));
		if (obj) return JSON.parse(await obj.text());
	}
	if (!fallback || fallback === '{}') return null;
	try {
		return JSON.parse(fallback);
	} catch {
		return null;
	}
}

async function storeDocument(env: VaultEnv, userId: string, revision: number, jsonText: string): Promise<void> {
	if (env.VAULT) {
		await env.VAULT.put(r2Key(userId), jsonText, {
			httpMetadata: { contentType: 'application/json; charset=utf-8' },
		});
	}
	const d1Body = env.VAULT ? '{}' : jsonText;
	await env.DB.prepare(
		`INSERT INTO vaults (user_id, revision, document, updated_at) VALUES (?, ?, ?, datetime('now'))
		 ON CONFLICT(user_id) DO UPDATE SET revision = excluded.revision, document = excluded.document, updated_at = excluded.updated_at`,
	)
		.bind(userId, revision, d1Body)
		.run();
}

export function r2Key(userId: string): string {
	return `vaults/${userId}.json`;
}

function tooLarge(bytes: number): string {
	const mb = (bytes / 1_000_000).toFixed(1);
	return `Snapshot too large (${mb} MB after compacting). Unsubscribe unused podcasts or delete old notes, then sync again.`;
}

function json(body: unknown): Response {
	return new Response(JSON.stringify(body), {
		headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
	});
}

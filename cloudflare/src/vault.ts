import { currentUser, writeAudit, clientIp } from './auth';
import { jsonError } from './safe';
import { mergeDocs } from '../../website/public/web/merge.js';

export type VaultEnv = { DB: D1Database };

const MAX_DOC = 4_000_000;

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
	if (!row) {
		return json({ ok: true, revision: 0, document: null, updatedAt: null });
	}
	return json({
		ok: true,
		revision: row.revision,
		document: JSON.parse(row.document),
		updatedAt: row.updated_at,
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
	const encoded = JSON.stringify(incoming);
	if (encoded.length > MAX_DOC) return jsonError('Snapshot too large', 413);
	const existing = await env.DB.prepare('SELECT revision, document FROM vaults WHERE user_id = ?')
		.bind(userId)
		.first<{ revision: number; document: string }>();
	const merged = existing ? mergeDocs(JSON.parse(existing.document), incoming) : incoming;
	const next = JSON.stringify(merged);
	if (next.length > MAX_DOC) return jsonError('Snapshot too large', 413);
	const revision = (existing?.revision || 0) + 1;
	await env.DB.prepare(
		`INSERT INTO vaults (user_id, revision, document, updated_at) VALUES (?, ?, ?, datetime('now'))
		 ON CONFLICT(user_id) DO UPDATE SET revision = excluded.revision, document = excluded.document, updated_at = excluded.updated_at`,
	)
		.bind(userId, revision, next)
		.run();
	await writeAudit(env.DB, { actorId: userId, action: 'vault.put', ip: clientIp(request) });
	return json({ ok: true, revision, document: merged });
}

function json(body: unknown): Response {
	return new Response(JSON.stringify(body), {
		headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
	});
}

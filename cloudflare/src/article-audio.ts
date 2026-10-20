import { currentUser } from './auth';
import { jsonError } from './safe';
import { xaiSpeechBytes } from './ai';
import { voiceId } from '../../website/public/web/ai.js';
import { audioByteRange, audioContentRange, speechHash, storedPieces } from '../../website/public/web/article-audio.js';

export type AudioEnv = { DB: D1Database; VAULT?: R2Bucket };

const ARTICLE_ID = /^r[0-9a-f]{16}$/;
const HASH = /^[0-9a-f]{16}$/;

export function speechObjectKey(userId: string, articleId: string, voice: string, hash: string, index: number): string {
	return `speech/${userId}/${articleId}/${voice}/${hash}/${index}.mp3`;
}

export async function handleArticleAudio(request: Request, url: URL, env: AudioEnv): Promise<Response | null> {
	if (url.pathname !== '/api/articles/audio') return null;
	const user = await currentUser(request, env);
	if (!user) return jsonError('Not signed in', 401);
	if (!env.VAULT) return jsonError('Audio storage is not configured', 503);
	if (request.method === 'GET') return getAudio(request, url, env.VAULT, user.id);
	if (request.method === 'POST') {
		const type = request.headers.get('content-type') || '';
		if (type.includes('audio/') || type.includes('octet-stream')) return storeAudio(request, url, env.VAULT, user.id);
		return putAudio(request, env.VAULT, user.id);
	}
	return jsonError('Method not allowed', 405);
}

async function getAudio(request: Request, url: URL, vault: R2Bucket, userId: string): Promise<Response> {
	const articleId = url.searchParams.get('id') || '';
	const hash = url.searchParams.get('hash') || '';
	const voice = voiceId(url.searchParams.get('voice') || '');
	if (!ARTICLE_ID.test(articleId) || !HASH.test(hash)) return jsonError('Article not found', 404);
	const index = url.searchParams.get('index');
	if (index == null || index === '') {
		const ready = await readyIndexes(vault, userId, articleId, voice, hash);
		return Response.json({ hash, voice, ready: [...ready].sort((a, b) => a - b) }, { headers: { 'cache-control': 'no-store' } });
	}
	const n = Number(index);
	if (!Number.isInteger(n) || n < 0 || n > 80) return jsonError('Article not found', 404);
	const range = audioByteRange(request.headers.get('Range'));
	const obj = await vault.get(speechObjectKey(userId, articleId, voice, hash, n), range ? { range } : undefined);
	if (!obj) return jsonError('Not ready', 404);
	const headers = new Headers({
		'content-type': 'audio/mpeg',
		'accept-ranges': 'bytes',
		'cache-control': 'private, max-age=86400',
	});
	const part = audioContentRange(obj.range, obj.size);
	if (part) {
		headers.set('content-range', `bytes ${part.start}-${part.end}/${part.size}`);
		headers.set('content-length', String(part.end - part.start + 1));
		return new Response(obj.body, { status: 206, headers });
	}
	if (obj.size) headers.set('content-length', String(obj.size));
	return new Response(obj.body, { status: 200, headers });
}

const SPEECH_MAX_BYTES = 32_000_000;

async function storeAudio(request: Request, url: URL, vault: R2Bucket, userId: string): Promise<Response> {
	const articleId = url.searchParams.get('id') || '';
	const hash = url.searchParams.get('hash') || '';
	const voice = voiceId(url.searchParams.get('voice') || '');
	const index = Number(url.searchParams.get('index') || '');
	if (!ARTICLE_ID.test(articleId) || !HASH.test(hash)) return jsonError('Article not found', 404);
	if (!Number.isInteger(index) || index < 0 || index > 80) return jsonError('Article not found', 404);
	const audio = await request.arrayBuffer();
	if (audio.byteLength < 64 || audio.byteLength > SPEECH_MAX_BYTES) return jsonError('Speech reply was empty');
	await vault.put(speechObjectKey(userId, articleId, voice, hash, index), audio, {
		httpMetadata: { contentType: 'audio/mpeg' },
	});
	return Response.json({ ready: true, hash, voice, index }, { headers: { 'cache-control': 'no-store' } });
}

async function putAudio(request: Request, vault: R2Bucket, userId: string): Promise<Response> {
	const body = (await request.json().catch(() => null)) as Record<string, unknown> | null;
	if (!body) return jsonError('Invalid JSON');
	const articleId = String(body.id || '');
	const text = String(body.text || '');
	const bearer = String(body.bearer || '').trim();
	const voice = voiceId(body.voice);
	if (!ARTICLE_ID.test(articleId)) return jsonError('Article not found', 404);
	if (!text || text.length > 40_000) return jsonError('Nothing to speak');
	if (!bearer) return jsonError('Sign in to xAI in settings, then sync.');
	const pieces = storedPieces(text);
	const hash = speechHash(text);
	if (!pieces.length) return Response.json({ ready: true, total: 0, hash, voice });
	const ready = await readyIndexes(vault, userId, articleId, voice, hash);
	let index = Number(body.index);
	if (!Number.isInteger(index) || index < 0 || index >= pieces.length) {
		index = 0;
		while (index < pieces.length && ready.has(index)) index += 1;
	}
	if (index >= pieces.length) {
		return Response.json({ ready: true, total: pieces.length, hash, voice, index: pieces.length - 1 });
	}
	if (!ready.has(index)) {
		const audio = await xaiSpeechBytes(pieces[index], voice, bearer, 1);
		await vault.put(speechObjectKey(userId, articleId, voice, hash, index), audio, {
			httpMetadata: { contentType: 'audio/mpeg' },
		});
		ready.add(index);
	}
	const done = ready.size >= pieces.length && [...Array(pieces.length).keys()].every((n) => ready.has(n));
	return Response.json(
		{ ready: done, total: pieces.length, hash, voice, index },
		{ headers: { 'cache-control': 'no-store' } },
	);
}

async function readyIndexes(vault: R2Bucket, userId: string, articleId: string, voice: string, hash: string): Promise<Set<number>> {
	const prefix = `speech/${userId}/${articleId}/${voice}/${hash}/`;
	const ready = new Set<number>();
	let cursor: string | undefined;
	do {
		const page = await vault.list({ prefix, cursor });
		for (const obj of page.objects) {
			const match = obj.key.match(/(\d+)\.mp3$/);
			if (match) ready.add(Number(match[1]));
		}
		cursor = page.truncated ? page.cursor : undefined;
	} while (cursor);
	return ready;
}

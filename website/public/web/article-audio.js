import { durationMs, fnv1a64 } from './reads.js';
import { SPEAK_STORE, speakPieces, voiceId } from './ai.js';

const ARTICLE_ID = /^r[0-9a-f]{16}$/;
const HASH = /^[0-9a-f]{16}$/;

export function speechHash(text) {
	return fnv1a64(String(text || ''));
}

export function storedPieces(text) {
	return speakPieces(text, SPEAK_STORE);
}

export function storedRows(text) {
	let cursor = 0;
	return storedPieces(text).map((piece, index) => {
		const slice = Math.max(1, durationMs(piece));
		const row = { index, text: piece, startMs: cursor, endMs: cursor + slice };
		cursor += slice;
		return row;
	});
}

export function speechObjectKey(userId, articleId, voice, hash, index) {
	if (!ARTICLE_ID.test(articleId) || !HASH.test(hash)) return '';
	const id = voiceId(voice);
	const n = Number(index);
	if (!Number.isInteger(n) || n < 0 || n > 80) return '';
	return `speech/${userId}/${articleId}/${id}/${hash}/${n}.mp3`;
}

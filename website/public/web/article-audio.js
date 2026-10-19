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

export function audioProgressLabel(job) {
	if (!job?.status) return '';
	const total = Math.max(0, Number(job.total) || 0);
	const done = Math.max(0, Number(job.done) || 0);
	const shown = total ? Math.min(done, total) : done;
	if (job.status === 'failed') {
		const reason = String(job.error || '').replace(/\s+/g, ' ').trim();
		const short = reason.length > 80 ? `${reason.slice(0, 77)}…` : reason;
		return short ? `audio failed · ${short}` : 'audio failed';
	}
	if (total > 0 && shown >= total) return 'audio ready';
	if ((job.status === 'preparing' || job.status === 'partial') && shown > 0) return `ready to play · ${shown} of ${total}`;
	if (job.status === 'preparing') return total ? `preparing audio ${shown} of ${total}` : 'preparing audio';
	if (total && shown > 0 && shown < total) return `audio ${shown} of ${total}`;
	return '';
}

export function nextSpeechIndex(total, ready, prefer) {
	const done = new Set((ready || []).map(Number));
	const wanted = Number(prefer);
	if (Number.isInteger(wanted) && wanted >= 0 && wanted < total && !done.has(wanted)) return wanted;
	for (let i = 0; i < total; i += 1) if (!done.has(i)) return i;
	return -1;
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

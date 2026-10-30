import { durationMs, fnv1a64 } from './reads.js';
import { SPEAK_STORE, speakPieces, voiceId } from './ai.js';

const ARTICLE_ID = /^r[0-9a-f]{16}$/;
const HASH = /^[0-9a-f]{16}$/;

export function speechBearer(secret, supplied) {
	const own = String(secret || '').trim();
	if (own) return own;
	return String(supplied || '').trim();
}

export function speechHash(text) {
	return fnv1a64(`${SPEAK_STORE}\n${String(text || '')}`);
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
	if (job.status === 'preparing' && total <= 1) return 'preparing audio';
	if (job.status === 'preparing') return `preparing audio ${shown} of ${total}`;
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

export function articleDownloadLabel(status, transfer) {
	if (status === 'failed') return '';
	if (status !== 'ready') return 'generating…';
	if (!transfer) return '';
	const received = Number(transfer.received) || 0;
	const total = Number(transfer.total) || 0;
	if (total <= 0 || received <= 0) return 'downloading…';
	if (received >= total) return '';
	const pct = Math.floor((received * 100) / total);
	return pct > 0 ? `${pct}%` : 'downloading…';
}

export function articleDownloadVisible(status) {
	return status === 'ready';
}

export function expectedSpeechBytes(durationMs) {
	const ms = Number(durationMs) || 0;
	if (ms <= 0) return 0;
	return Math.round(ms * 8);
}

export function articleGenerationLabel(status, received, expected) {
	if (status === 'ready' || status === 'failed') return '';
	const got = Number(received) || 0;
	const total = Number(expected) || 0;
	if (total <= 0 || got <= 0) return 'generating…';
	const pct = Math.min(99, Math.floor((got * 100) / total));
	return pct > 0 ? `generating… ${pct}%` : 'generating…';
}

export function articleFileName(title) {
	const clean = String(title || '').replace(/[^\w. -]+/g, '').replace(/\s+/g, ' ').trim().slice(0, 80);
	return `${clean || 'article'}.mp3`;
}

export function audioByteRange(header) {
	const match = /^bytes=(\d+)-(\d*)$/i.exec(String(header || '').trim());
	if (!match) return null;
	const offset = Number(match[1]);
	const end = match[2] === '' ? null : Number(match[2]);
	if (!Number.isInteger(offset) || offset < 0) return null;
	if (end != null && (!Number.isInteger(end) || end < offset)) return null;
	return end == null ? { offset } : { offset, length: end - offset + 1 };
}

export function audioContentRange(range, size) {
	if (!range || !Number.isFinite(size) || size <= 0) return null;
	if (Number.isFinite(range.suffix) && range.suffix > 0) {
		const length = Math.min(range.suffix, size);
		const start = size - length;
		if (start === 0 && length === size) return null;
		return { start, end: start + length - 1, size };
	}
	const offset = Number.isFinite(range.offset) ? range.offset : null;
	const length = Number.isFinite(range.length) ? range.length : null;
	if (offset == null && length == null) return null;
	const start = offset ?? Math.max(0, size - (length ?? 0));
	const span = length ?? size - start;
	if (!Number.isFinite(start) || !Number.isFinite(span) || span <= 0 || start < 0 || start + span > size) return null;
	if (start === 0 && span === size) return null;
	return { start, end: start + span - 1, size };
}

export function mediaErrorStopsPlayback(code, partial) {
	if (partial) return false;
	return code === 2 || code === 3 || code === 4;
}

export function showArticleDownload() {
	return false;
}

export const SPEECH_START_BYTES = 16_000;

export function speechUsesMediaSource(supported) {
	return supported === true;
}

export function speechStartReady(received, started) {
	return !started && Number(received) > SPEECH_START_BYTES;
}

export function playbackKind({ ready, canLive } = {}) {
	if (ready) return 'stored';
	if (canLive) return 'live';
	return 'hold';
}

export function attachTruncatedSpeech() {
	return false;
}

export function liveAudioPath(id, token) {
	return `/api/articles/live?id=${encodeURIComponent(id)}&t=${encodeURIComponent(token)}`;
}

export function audioIsFullRead(header) {
	const range = audioByteRange(header);
	if (!range) return true;
	return range.offset === 0 && range.length == null;
}

export function staleMediaError(current, seen) {
	return current !== seen;
}

export function playedWordMs(audioMs, audioDurationMs, textDurationMs) {
	const audio = Number(audioMs) || 0;
	const audioDur = Number(audioDurationMs);
	const textDur = Number(textDurationMs) || 0;
	if (!Number.isFinite(audioDur) || audioDur <= 0 || textDur <= 0) return Math.max(0, audio);
	return Math.min(textDur, Math.round((textDur * Math.max(0, audio)) / audioDur));
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

import assert from 'node:assert/strict';
import test from 'node:test';
import { articleDownloadLabel, articleDownloadVisible, articleFileName, articleGenerationLabel, attachTruncatedSpeech, audioByteRange, audioContentRange, audioIsFullRead, audioProgressLabel, expectedSpeechBytes, liveAudioPath, mediaErrorStopsPlayback, nextSpeechIndex, playbackKind, playedWordMs, showArticleDownload, speechHash, speechObjectKey, speechStartReady, speechUsesMediaSource, staleMediaError, storedPieces } from '../public/web/article-audio.js';

test('saved article audio is keyed per account, article, and voice', () => {
	const text = 'Paced bottle feeding lets the baby set the pace. '.repeat(40);
	const hash = speechHash(text);
	assert.equal(hash.length, 16);
	assert.notEqual(hash, speechHash(`${text} more`));
	assert.equal(
		speechObjectKey('user', 'r046a4934057f51cf', 'Leo', hash, 0),
		`speech/user/r046a4934057f51cf/leo/${hash}/0.mp3`,
	);
	assert.equal(speechObjectKey('user', 'nope', 'leo', hash, 0), '');
	assert.equal(storedPieces(text).length, 1);
	assert.equal(storedPieces(`${text} `.repeat(40)).every((piece) => piece.length <= 60_000), true);
});

test('speech asks xAI directly and saves the finished file', async () => {
	const { xaiSpeechPayload, SPEECH_WAIT_MS } = await import('../public/web/ai.js');
	const payload = xaiSpeechPayload('Hello there.', 'Leo', 1);
	assert.equal(payload.voice_id, 'leo');
	assert.equal(payload.text, 'Hello there.');
	assert.equal(payload.output_format.codec, 'mp3');
	assert.equal(payload.optimize_streaming_latency, 1);
	assert.equal(SPEECH_WAIT_MS > 120_000, true);
	const { speechStreamQuery, speechTextFrames } = await import('../public/web/ai.js');
	const query = speechStreamQuery('Leo', 1);
	assert.equal(query.get('voice'), 'leo');
	assert.equal(query.get('codec'), 'mp3');
	assert.equal(query.get('optimize_streaming_latency'), '1');
	const frames = speechTextFrames('a'.repeat(9_000));
	assert.equal(frames.at(-1).type, 'text.done');
	assert.equal(frames.filter((row) => row.type === 'text.delta').length, 3);
	assert.equal(frames.slice(0, -1).map((row) => row.delta).join(''), 'a'.repeat(9_000));
	const app = await import('node:fs').then((fs) => fs.readFileSync(new URL('../public/web/app.js', import.meta.url), 'utf8'));
	assert.match(app, /https:\/\/api\.x\.ai\/v1\/tts/);
	assert.match(app, /playbackKind\(/);
	assert.match(app, /liveAudioPath\(/);
	assert.doesNotMatch(app, /Boolean\(partial\)/);
	assert.doesNotMatch(app, /createObjectURL\(new Blob\(parts/);
	assert.match(app, /content-type': 'audio\/mpeg'/);
	assert.doesNotMatch(app, /timeout: 300_000/);
});

test('article audio progress says how far, when ready, and when it failed', () => {
	assert.equal(audioProgressLabel(null), '');
	assert.equal(audioProgressLabel({ status: 'preparing', done: 0, total: 1 }), 'preparing audio');
	assert.equal(audioProgressLabel({ status: 'preparing', done: 0, total: 17 }), 'preparing audio 0 of 17');
	assert.equal(audioProgressLabel({ status: 'preparing', done: 3, total: 17 }), 'ready to play · 3 of 17');
	assert.equal(audioProgressLabel({ status: 'partial', done: 3, total: 17 }), 'ready to play · 3 of 17');
	assert.equal(audioProgressLabel({ status: 'ready', done: 17, total: 17 }), 'audio ready');
	assert.equal(audioProgressLabel({ status: 'failed', done: 3, total: 17, error: 'Speech HTTP 429' }), 'audio failed · Speech HTTP 429');
	assert.equal(audioProgressLabel({ status: 'failed' }), 'audio failed');
});

test('the clip being played is generated before the rest', () => {
	assert.equal(nextSpeechIndex(17, [], 0), 0);
	assert.equal(nextSpeechIndex(17, [0], null), 1);
	assert.equal(nextSpeechIndex(17, [0, 1, 2], 5), 5);
	assert.equal(nextSpeechIndex(17, [0, 1, 2, 5], 5), 3);
	assert.equal(nextSpeechIndex(17, [0, 1, 2], 2), 3);
	assert.equal(nextSpeechIndex(3, [0, 1, 2], null), -1);
});

test('a stored article can be streamed from a byte range, and a source switch is not a failure', () => {
	assert.equal(audioByteRange(''), null);
	assert.deepEqual(audioByteRange('bytes=0-1'), { offset: 0, length: 2 });
	assert.deepEqual(audioByteRange('bytes=100-'), { offset: 100 });
	assert.equal(audioByteRange('bytes=9-1'), null);
	assert.equal(mediaErrorStopsPlayback(1), false);
	assert.equal(mediaErrorStopsPlayback(4), true);
	assert.equal(mediaErrorStopsPlayback(null), false);
	assert.equal(audioContentRange({ suffix: undefined, offset: undefined }, 16748), null);
	assert.equal(audioContentRange({ offset: NaN }, 16748), null);
	assert.deepEqual(audioContentRange({ offset: 0, length: 1024 }, 16748), { start: 0, end: 1023, size: 16748 });
	assert.equal(audioContentRange({ offset: 0, length: 16748 }, 16748), null);
	assert.deepEqual(audioContentRange({ suffix: 100 }, 1000), { start: 900, end: 999, size: 1000 });
});

test('an article download says generating until the file exists, never 0%', () => {
	assert.equal(articleDownloadLabel(''), 'generating…');
	assert.equal(articleDownloadLabel('preparing'), 'generating…');
	assert.equal(articleDownloadLabel('preparing', { received: 0, total: 0 }), 'generating…');
	assert.equal(articleDownloadLabel('ready'), '');
	assert.equal(articleDownloadLabel('ready', { received: 0, total: 0 }), 'downloading…');
	assert.equal(articleDownloadLabel('ready', { received: 10, total: 0 }), 'downloading…');
	assert.equal(articleDownloadLabel('ready', { received: 37, total: 100 }), '37%');
	assert.equal(articleDownloadLabel('failed'), '');
	assert.equal(articleFileName('The Real Title'), 'The Real Title.mp3');
	assert.equal(articleFileName('a/b?'), 'ab.mp3');
	assert.equal(articleFileName(''), 'article.mp3');
	assert.equal(articleDownloadVisible('preparing'), false);
	assert.equal(articleDownloadVisible(''), false);
	assert.equal(articleDownloadVisible('failed'), false);
	assert.equal(articleDownloadVisible('ready'), true);
	assert.equal(showArticleDownload(), false);
	assert.equal(speechUsesMediaSource(false), false);
	assert.equal(speechUsesMediaSource(true), true);
	assert.equal(speechStartReady(16_001, false), true);
	assert.equal(speechStartReady(16_000, false), false);
	assert.equal(speechStartReady(20_000, true), false);
	assert.equal(mediaErrorStopsPlayback(3, true), false);
	assert.equal(mediaErrorStopsPlayback(3, false), true);
	assert.equal(expectedSpeechBytes(1000), 8000);
	assert.equal(articleGenerationLabel('ready', 8000, 8000), '');
	assert.equal(articleGenerationLabel('failed', 10, 8000), '');
	assert.equal(articleGenerationLabel('preparing', 0, 8000), 'generating…');
	assert.equal(articleGenerationLabel('preparing', 2960, 8000), 'generating… 37%');
	assert.equal(articleGenerationLabel('preparing', 9000, 8000), 'generating… 99%');
});

test('a saved article streams from the account url, and a truncated mp3 is not a stream', () => {
	assert.equal(playbackKind({ ready: true, canLive: true }), 'stored');
	assert.equal(playbackKind({ ready: false, canLive: true }), 'live');
	assert.equal(playbackKind({ ready: false, canLive: false }), 'hold');
	assert.equal(attachTruncatedSpeech(), false);
	assert.equal(liveAudioPath('r046a4934057f51cf', 7), '/api/articles/live?id=r046a4934057f51cf&t=7');
	assert.equal(audioIsFullRead(''), true);
	assert.equal(audioIsFullRead('bytes=0-'), true);
	assert.equal(audioIsFullRead('bytes=0-1'), false);
	assert.equal(audioIsFullRead('bytes=100-'), false);
	assert.equal(staleMediaError(2, 1), true);
	assert.equal(staleMediaError(2, 2), false);
	assert.equal(playedWordMs(15000, 30000, 60000), 30000);
	assert.equal(playedWordMs(1000, Infinity, 60000), 1000);
});

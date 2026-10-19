import assert from 'node:assert/strict';
import test from 'node:test';
import { audioByteRange, audioProgressLabel, mediaErrorStopsPlayback, nextSpeechIndex, speechHash, speechObjectKey, storedPieces } from '../public/web/article-audio.js';

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
	assert.equal(SPEECH_WAIT_MS > 120_000, true);
	const app = await import('node:fs').then((fs) => fs.readFileSync(new URL('../public/web/app.js', import.meta.url), 'utf8'));
	assert.match(app, /https:\/\/api\.x\.ai\/v1\/tts/);
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
});

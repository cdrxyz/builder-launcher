import assert from 'node:assert/strict';
import test from 'node:test';
import { audioProgressLabel, nextSpeechIndex, speechHash, speechObjectKey, storedPieces } from '../public/web/article-audio.js';

test('saved article audio is keyed per account, article, and voice', () => {
	const text = 'Paced bottle feeding lets the baby set the pace. '.repeat(40);
	const hash = speechHash(text);
	assert.equal(hash.length, 16);
	assert.equal(
		speechObjectKey('user', 'r046a4934057f51cf', 'Leo', hash, 0),
		`speech/user/r046a4934057f51cf/leo/${hash}/0.mp3`,
	);
	assert.equal(speechObjectKey('user', 'nope', 'leo', hash, 0), '');
	assert.ok(storedPieces(text).length > 1);
	assert.ok(storedPieces(text).every((piece) => piece.length <= 1500));
});

test('article audio progress says how far, when ready, and when it failed', () => {
	assert.equal(audioProgressLabel(null), '');
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

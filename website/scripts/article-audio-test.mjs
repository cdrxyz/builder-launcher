import assert from 'node:assert/strict';
import test from 'node:test';
import { speechHash, speechObjectKey, storedPieces } from '../public/web/article-audio.js';

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

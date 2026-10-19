import assert from 'node:assert/strict';
import test from 'node:test';
import { speakPieces, speechPlan, ttsSpeed } from '../public/web/ai.js';

test('article speech prefers a signed-in xAI account', () => {
	const signed = speechPlan({ provider: 'XAI', oauthAccess: 'tok', oauthExpiresAtEpochMs: Date.now() + 600_000 }, Date.now());
	assert.equal(signed.engine, 'xai');
	assert.equal(signed.base, 'https://api.x.ai/v1');
	const other = speechPlan({ provider: 'OPENAI', apiKey: 'sk', oauthAccess: 'xai-tok', oauthExpiresAtEpochMs: Date.now() + 600_000 }, Date.now());
	assert.equal(other.engine, 'xai');
	const groq = speechPlan({ provider: 'GROQ', apiKey: 'gsk' }, Date.now());
	assert.equal(groq.engine, 'groq');
	assert.equal(groq.model, 'playai-tts');
	const none = speechPlan({ provider: 'OPENAI', apiKey: 'sk' }, Date.now());
	assert.equal(none.engine === 'browser' || Boolean(none.error), true);
});

test('speech pieces stay under the request cap and speed stays in range', () => {
	const text = Array.from({ length: 40 }, (_, i) => `Sentence ${i} has enough words to count.`).join(' ');
	const pieces = speakPieces(text, 180);
	assert.ok(pieces.length > 1);
	assert.ok(pieces.every((row) => row.length <= 180));
	assert.equal(pieces.join(' ').includes('Sentence 0'), true);
	assert.equal(ttsSpeed(2), 1.5);
	assert.equal(ttsSpeed(0.2), 0.7);
	assert.equal(ttsSpeed(1.24), 1.2);
});

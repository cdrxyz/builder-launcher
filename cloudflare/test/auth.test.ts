import assert from 'node:assert/strict';
import test from 'node:test';
import { hashPassword, isValidEmail, isValidPassword, verifyPassword } from '../src/crypto.ts';

test('argon2id round trip', async () => {
	const stored = await hashPassword('correct horse');
	assert.equal(await verifyPassword('correct horse', stored), true);
	assert.equal(await verifyPassword('wrong', stored), false);
});

test('email and password bounds', () => {
	assert.equal(isValidEmail('a@b.co'), true);
	assert.equal(isValidEmail('nope'), false);
	assert.equal(isValidPassword('short'), false);
	assert.equal(isValidPassword('longenough'), true);
});

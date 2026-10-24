import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { DatabaseSync } from 'node:sqlite';
import { handleAuth } from '../src/auth.ts';
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

test('a refresh token mints a new session after the access token expires', async () => {
	const db = new DatabaseSync(':memory:');
	db.exec(readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));
	const env = { DB: d1(db) };
	const signup = await handleAuth(jsonRequest('/api/auth/signup', { email: 'a@b.co', password: 'longenough' }), new URL('https://builder.cdr.xyz/api/auth/signup'), env);
	const created = await signup.json();
	assert.ok(created.token);
	assert.ok(created.refreshToken);
	assert.notEqual(created.refreshToken, created.token);
	db.prepare("UPDATE sessions SET expires_at = datetime('now', '-1 day')").run();
	const expired = await handleAuth(bearer('/api/auth/me', created.token), new URL('https://builder.cdr.xyz/api/auth/me'), env);
	assert.equal(expired.status, 401);
	const refreshed = await handleAuth(
		jsonRequest('/api/auth/refresh', { refreshToken: created.refreshToken }),
		new URL('https://builder.cdr.xyz/api/auth/refresh'),
		env,
	);
	assert.equal(refreshed.status, 200);
	const next = await refreshed.json();
	assert.ok(next.token);
	assert.notEqual(next.token, created.token);
	assert.equal(next.refreshToken, created.refreshToken);
	const me = await handleAuth(bearer('/api/auth/me', next.token), new URL('https://builder.cdr.xyz/api/auth/me'), env);
	assert.equal(me.status, 200);
	const dead = await handleAuth(
		jsonRequest('/api/auth/refresh', { refreshToken: 'not-a-token' }),
		new URL('https://builder.cdr.xyz/api/auth/refresh'),
		env,
	);
	assert.equal(dead.status, 401);
});

function jsonRequest(path, body) {
	return new Request(`https://builder.cdr.xyz${path}`, {
		method: 'POST',
		headers: { 'content-type': 'application/json' },
		body: JSON.stringify(body),
	});
}

function bearer(path, token) {
	return new Request(`https://builder.cdr.xyz${path}`, { headers: { authorization: `Bearer ${token}` } });
}

function d1(db) {
	return {
		prepare(sql) {
			return {
				bind(...args) {
					return {
						async first() {
							return db.prepare(sql).get(...args) ?? null;
						},
						async run() {
							db.prepare(sql).run(...args);
							return { success: true };
						},
					};
				},
			};
		},
	};
}

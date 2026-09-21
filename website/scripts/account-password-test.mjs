import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import {
	applyPasswordManagerAttrs,
	fieldAutocomplete,
	fillAccountForm,
	hideFromPasswordManagers,
	passwordCredentialData,
	requestAccountCredential,
	storeAccountCredential,
} from '../public/web/account.js';

test('account login fields prompt the password manager', async () => {
	assert.equal(fieldAutocomplete('email', 'email'), 'username');
	assert.equal(fieldAutocomplete('password', 'password'), 'current-password');
	assert.equal(fieldAutocomplete('password', 'password', 'signup'), 'new-password');
	assert.equal(fieldAutocomplete('secretKey', 'password'), 'off');
	assert.equal(hideFromPasswordManagers('secretKey'), true);
	assert.equal(hideFromPasswordManagers('password'), false);
	const email = { setAttribute() {}, id: '' };
	applyPasswordManagerAttrs(email, 'email', 'email');
	assert.equal(email.autocomplete, 'username');
	assert.equal(email.id, 'builder-account-email');
	const secret = { attrs: {}, setAttribute(k, v) { this.attrs[k] = v; } };
	applyPasswordManagerAttrs(secret, 'secretKey', 'password');
	assert.equal(secret.autocomplete, 'off');
	assert.equal(secret.attrs['data-1p-ignore'], 'true');
	assert.deepEqual(passwordCredentialData(' me@adrw.xyz ', 'hunter2'), {
		id: 'me@adrw.xyz',
		name: 'me@adrw.xyz',
		password: 'hunter2',
	});
	assert.equal(passwordCredentialData('', 'x'), null);
	const form = {
		email: { value: '' },
		password: { value: '' },
		querySelector(sel) {
			if (sel === '[name="email"]') return this.email;
			if (sel === '[name="password"]') return this.password;
			return null;
		},
	};
	assert.equal(fillAccountForm(form, { email: 'me@adrw.xyz', password: 'secret' }), true);
	assert.equal(form.email.value, 'me@adrw.xyz');
	assert.equal(form.password.value, 'secret');
	const stored = [];
	class FakeCred {
		constructor(data) { this.data = data; }
	}
	assert.equal(await storeAccountCredential('a@b.c', 'pw', { store: async (c) => stored.push(c) }, FakeCred), true);
	assert.equal(stored[0].data.id, 'a@b.c');
	assert.equal(await storeAccountCredential('a@b.c', 'pw', {}), false);
	const cred = await requestAccountCredential({
		get: async (opts) => {
			assert.equal(opts.password, true);
			assert.equal(opts.mediation, 'optional');
			return { type: 'password', id: 'me@adrw.xyz', password: 'saved' };
		},
	});
	assert.deepEqual(cred, { email: 'me@adrw.xyz', password: 'saved' });
	assert.equal(await requestAccountCredential({ get: async () => { throw new Error('no'); } }), null);
	const app = await readFile(new URL('../public/web/app.js', import.meta.url), 'utf8');
	assert.match(app, /form\.id = 'builder-account-form'/);
	assert.match(app, /signIn\.type = 'submit'/);
	assert.match(app, /promptAccountPassword\(form\)/);
	assert.match(app, /await storeAccountCredential\(email, password\)/);
	assert.match(app, /applyPasswordManagerAttrs\(input, name, type\)/);
	assert.doesNotMatch(app, /button\('sign in', \(\) => submitAccount\(form, 'login'\)/);
});

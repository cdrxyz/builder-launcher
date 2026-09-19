import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { decrypt, encodeUtf8, encrypt, MAGIC } from '../public/web/crypto.js';
import { doneTodos, noteTitle, notesByEdited, openTodos, seedNote } from '../public/web/items.js';
import { renderMarkdown } from '../public/web/markdown.js';
import { credentialsReady, objectUrl, regionFor, sign } from '../public/web/s3.js';

test('regionFor matches Kotlin', () => {
	assert.equal(regionFor('https://abc.r2.cloudflarestorage.com'), 'auto');
	assert.equal(regionFor('https://s3.us-west-2.amazonaws.com'), 'us-west-2');
	assert.equal(regionFor('https://s3.amazonaws.com'), 'us-east-1');
});

test('objectUrl is path-style', () => {
	assert.equal(
		objectUrl('https://abc.r2.cloudflarestorage.com/', 'my-bucket'),
		'https://abc.r2.cloudflarestorage.com/my-bucket/builder-launcher/backup.enc',
	);
});

test('credentialsReady does not need encryption key', () => {
	assert.equal(credentialsReady('https://x', 'b', 'a', 's'), true);
	assert.equal(credentialsReady('https://x', 'b', 'a', ''), false);
});

test('sign is stable for fixed AWS example inputs', async () => {
	const payload = encodeUtf8('hello');
	const headers = await sign({
		method: 'PUT',
		url: 'https://s3.us-east-1.amazonaws.com/bucket/builder-launcher/backup.enc',
		accessKey: 'AKIAIOSFODNN7EXAMPLE',
		secretKey: 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
		region: 'us-east-1',
		payload,
		amzDate: '20130524T000000Z',
		extraHeaders: { 'content-type': 'application/octet-stream' },
	});
	const auth = headers.authorization;
	assert.match(
		auth,
		/^AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE\/20130524\/us-east-1\/s3\/aws4_request/,
	);
	assert.equal(auth.split('Signature=')[1].length, 64);
	const again = await sign({
		method: 'PUT',
		url: 'https://s3.us-east-1.amazonaws.com/bucket/builder-launcher/backup.enc',
		accessKey: 'AKIAIOSFODNN7EXAMPLE',
		secretKey: 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
		region: 'us-east-1',
		payload,
		amzDate: '20130524T000000Z',
		extraHeaders: { 'content-type': 'application/octet-stream' },
	});
	assert.equal(auth, again.authorization);
});

test('crypto roundTrip', async () => {
	const plain = encodeUtf8('hello builder');
	const blob = await encrypt(plain, 'passphrase');
	assert.equal(new TextDecoder().decode(blob.subarray(0, 4)), MAGIC);
	assert.deepEqual(await decrypt(blob, 'passphrase'), plain);
});

test('wrong key fails', async () => {
	const blob = await encrypt(encodeUtf8('secret'), 'one');
	await assert.rejects(() => decrypt(blob, 'two'), /Wrong encryption key/);
});

test('notes and todos split like the phone', () => {
	const items = [
		{ id: '1', kind: 'todo', text: 'buy milk', createdAt: 1, completedAt: null, updatedAt: 0 },
		{ id: '2', kind: 'todo', text: 'done', createdAt: 1, completedAt: 9, updatedAt: 0 },
		{ id: '3', kind: 'note', text: '# Hello\nbody', createdAt: 1, updatedAt: 8 },
		{ id: '4', kind: 'note', text: 'older', createdAt: 2, updatedAt: 0 },
	];
	assert.deepEqual(
		openTodos(items).map((item) => item.text),
		['buy milk'],
	);
	assert.deepEqual(
		doneTodos(items).map((item) => item.text),
		['done'],
	);
	assert.deepEqual(
		notesByEdited(items).map((item) => noteTitle(item.text)),
		['Hello', 'older'],
	);
	assert.equal(seedNote('idea'), '# idea');
});

test('markdown escapes html', () => {
	const html = renderMarkdown('# Hi\n<script>x</script>\n- a');
	assert.match(html, /<h1>Hi<\/h1>/);
	assert.match(html, /&lt;script&gt;/);
	assert.doesNotMatch(html, /<script>/);
});

test('web shell files exist', async () => {
	for (const name of ['index.html', 'app.js', 'sw.js', 'manifest.webmanifest', 'app.css']) {
		const text = await readFile(new URL(`../public/web/${name}`, import.meta.url), 'utf8');
		assert.ok(text.length > 20, name);
	}
});

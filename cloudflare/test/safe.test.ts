import assert from 'node:assert/strict';
import test from 'node:test';
import { isSafeHttpsUrl, isYahooHost, isOpenMeteoHost, s3EndpointOk } from '../src/safe.ts';

test('rejects private feed hosts', () => {
	assert.equal(isSafeHttpsUrl('http://example.com/rss'), null);
	assert.equal(isSafeHttpsUrl('https://localhost/rss'), null);
	assert.equal(isSafeHttpsUrl('https://127.0.0.1/rss'), null);
	assert.equal(isSafeHttpsUrl('https://10.0.0.3/rss'), null);
	assert.equal(isSafeHttpsUrl('https://192.168.1.8/rss'), null);
	assert.equal(isSafeHttpsUrl('https://169.254.169.254/latest/meta-data'), null);
	assert.ok(isSafeHttpsUrl('https://feeds.example.com/rss.xml'));
});

test('yahoo host allowlist', () => {
	assert.equal(isYahooHost('query1.finance.yahoo.com'), true);
	assert.equal(isYahooHost('evil.example'), false);
});

test('s3 endpoint must be public https', () => {
	assert.equal(s3EndpointOk('https://abc.r2.cloudflarestorage.com'), true);
	assert.equal(s3EndpointOk('http://abc.r2.cloudflarestorage.com'), false);
	assert.equal(s3EndpointOk('https://127.0.0.1:9000'), false);
});

test('open-meteo host allowlist', () => {
	assert.equal(isOpenMeteoHost('api.open-meteo.com'), true);
	assert.equal(isOpenMeteoHost('geocoding-api.open-meteo.com'), true);
	assert.equal(isOpenMeteoHost('air-quality-api.open-meteo.com'), true);
	assert.equal(isOpenMeteoHost('evil.example'), false);
});

test('upstream fetch timeout is short enough that a hung RSS host cannot stall pull', async () => {
	const { UPSTREAM_TIMEOUT_MS } = await import('../src/safe.ts');
	assert.equal(UPSTREAM_TIMEOUT_MS, 12_000);
	assert.ok(UPSTREAM_TIMEOUT_MS < 30_000);
});

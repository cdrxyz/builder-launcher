import assert from 'node:assert/strict';
import test from 'node:test';
import { isPublicHttps, parseChatPayload, parseRefresh, planUpstream, readHermesSse } from '../../website/public/web/ai.js';

test('fixed providers ignore a swapped base', () => {
	const swapped = planUpstream({
		provider: 'XAI',
		base: 'https://evil.example/v1',
		model: 'grok-4.6',
		bearer: 'tok',
		messages: [{ role: 'user', content: 'hi' }],
	});
	assert.equal('error' in swapped, true);
});

test('xAI plan stays on api.x.ai', () => {
	const plan = planUpstream({
		provider: 'XAI',
		base: 'https://api.x.ai/v1',
		bearer: 'tok',
		messages: [{ role: 'user', content: 'hi' }],
	});
	assert.equal('error' in plan, false);
	if (!('error' in plan)) {
		assert.equal(plan.url, 'https://api.x.ai/v1');
		assert.equal(plan.kind, 'openai');
	}
});

test('LAN Hermes is refused', () => {
	const plan = planUpstream({
		provider: 'HERMES',
		base: 'http://192.168.1.10:9119',
		messages: [{ role: 'user', content: 'hi' }],
	});
	assert.equal('error' in plan, true);
	assert.equal(isPublicHttps('http://192.168.1.10:9119'), false);
	assert.equal(isPublicHttps('https://hermes.example.com'), true);
});

test('openai and anthropic payloads extract text', () => {
	assert.equal(parseChatPayload('{"choices":[{"message":{"content":"ok"}}]}', 'openai'), 'ok');
	assert.equal(parseChatPayload('{"content":[{"text":"ok"}]}', 'anthropic'), 'ok');
});

test('hermes sse keeps token text and surfaces errors', () => {
	const ok = 'event: token\ndata: {"text":"Hi"}\n\nevent: token\ndata: {"text":" there"}\n\n';
	assert.equal(readHermesSse(ok), 'Hi there');
	const bad = 'event: error\ndata: {"error":"nope"}\n\n';
	assert.equal(readHermesSse(bad), 'nope');
});

test('refresh keeps the previous token when the provider omits it', () => {
	const tokens = parseRefresh('{"access_token":"new","expires_in":120}', 1_000, 'old-refresh');
	assert.equal(tokens?.accessToken, 'new');
	assert.equal(tokens?.refreshToken, 'old-refresh');
	assert.equal(tokens?.expiresAtEpochMs, 1_000 + 120_000);
});

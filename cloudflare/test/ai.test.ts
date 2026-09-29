import assert from 'node:assert/strict';
import test from 'node:test';
import {
	anthropicTools,
	calculate,
	continueAnthropic,
	continueOpenAi,
	droppedFields,
	formatHits,
	isPublicHttps,
	openaiTools,
	parseChatPayload,
	parseDuckDuckGo,
	parseModelTurn,
	parseRefresh,
	planUpstream,
	privacyExtra,
	publicPageUrl,
	readHermesSse,
	stripHtml,
} from '../../website/public/web/ai.js';

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

test('privacy flags are request fields the provider documents', () => {
	assert.deepEqual(privacyExtra('OPENAI'), { store: false });
	assert.deepEqual(privacyExtra('GENERIC'), { store: false });
	assert.deepEqual(privacyExtra('OPENROUTER'), { provider: { zdr: true, data_collection: 'deny' } });
	assert.deepEqual(privacyExtra('ANTHROPIC'), {});
	assert.deepEqual(privacyExtra('HERMES'), {});
	assert.deepEqual(privacyExtra('OLLAMA'), {});
	assert.equal(planUpstream({
		provider: 'OPENAI',
		bearer: 'tok',
		messages: [{ role: 'user', content: 'hi' }],
	}).provider, 'OPENAI');
});

test('basic tools are web search, page fetch, and calculate', () => {
	const names = openaiTools().map((tool) => tool.function.name);
	assert.deepEqual(names, ['web_search', 'web_fetch', 'calculate']);
	assert.deepEqual(anthropicTools().map((tool) => tool.name), names);
	assert.equal(openaiTools()[0].function.parameters.required[0], 'query');
	assert.equal(anthropicTools()[2].input_schema.required[0], 'expression');
});

test('openai and anthropic turns keep tool calls', () => {
	const openai = parseModelTurn(
		JSON.stringify({
			choices: [{
				finish_reason: 'tool_calls',
				message: {
					content: null,
					tool_calls: [{
						id: 'call_1',
						type: 'function',
						function: { name: 'web_search', arguments: '{"query":"cedar labs"}' },
					}],
				},
			}],
		}),
		'openai',
	);
	assert.equal(openai.calls[0].name, 'web_search');
	assert.equal(openai.calls[0].arguments, '{"query":"cedar labs"}');
	const anthropic = parseModelTurn(
		JSON.stringify({
			stop_reason: 'tool_use',
			content: [
				{ type: 'text', text: 'Checking.' },
				{ type: 'tool_use', id: 'toolu_1', name: 'calculate', input: { expression: '2+2' } },
			],
		}),
		'anthropic',
	);
	assert.equal(anthropic.text, 'Checking.');
	assert.equal(anthropic.calls[0].name, 'calculate');
	assert.equal(JSON.parse(anthropic.calls[0].arguments).expression, '2+2');
});

test('tool results are appended for the next turn', () => {
	const turn = {
		text: '',
		calls: [{ id: 'call_1', name: 'web_search', arguments: '{"query":"x"}' }],
		blocks: [],
	};
	const openai = continueOpenAi(
		[{ role: 'user', content: 'hi' }],
		turn,
		[{ id: 'call_1', content: '1. Example\n   https://example.com' }],
	);
	assert.equal(openai.at(-1).role, 'tool');
	assert.equal(openai.at(-1).tool_call_id, 'call_1');
	const anthropic = continueAnthropic(
		[{ role: 'user', content: 'hi' }],
		{ ...turn, text: 'Checking.', blocks: [{ type: 'tool_use', id: 'toolu_1', name: 'calculate', input: { expression: '2+2' } }] },
		[{ id: 'toolu_1', content: '4' }],
	);
	assert.equal(anthropic.at(-1).role, 'user');
	assert.equal(anthropic.at(-1).content[0].type, 'tool_result');
});

test('duckduckgo html keeps public https hits and unwraps redirects', () => {
	const html = `
		<a class="result__a" href="https://duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpost&rut=1">Example post</a>
		<a class="result__snippet">A short snippet.</a>
		<a class="result-link" href="http://192.168.1.1/secret">Local</a>
		<td class="result-snippet">no</td>
	`;
	const hits = parseDuckDuckGo(html);
	assert.equal(hits.length, 1);
	assert.equal(hits[0].url, 'https://example.com/post');
	assert.equal(hits[0].title, 'Example post');
	assert.match(formatHits(hits), /example.com\/post/);
	assert.equal(formatHits([]), 'No results.');
});

test('page fetch rejects private hosts and strips tags', () => {
	assert.equal(publicPageUrl('https://example.com/a'), 'https://example.com/a');
	assert.equal(publicPageUrl('http://example.com/a'), '');
	assert.equal(publicPageUrl('https://user:pw@example.com/a'), '');
	assert.equal(publicPageUrl('https://127.0.0.1/latest'), '');
	assert.equal(publicPageUrl('https://169.254.169.254/'), '');
	assert.equal(stripHtml('<script>alert(1)</script><p>Hello <b>there</b></p>'), 'Hello there');
});

test('calculate evaluates arithmetic and refuses everything else', () => {
	assert.equal(calculate('2+2'), '4');
	assert.equal(calculate('(3*4)/2'), '6');
	assert.equal(calculate('sqrt(9)'), '3');
	assert.equal(calculate('process.exit(1)'), 'Could not calculate.');
	assert.equal(calculate(''), 'Could not calculate.');
});

test('unknown privacy or tool fields are the only reason to drop them', () => {
	assert.deepEqual(droppedFields('Unrecognized request argument supplied: store'), { privacy: true, tools: false });
	assert.deepEqual(droppedFields('tools is not supported by this model'), { privacy: false, tools: true });
	assert.deepEqual(droppedFields('No endpoints found that match your data policy (ZDR)'), { privacy: false, tools: false });
	assert.deepEqual(droppedFields('invalid api key'), { privacy: false, tools: false });
});

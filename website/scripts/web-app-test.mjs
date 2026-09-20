import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { decrypt, encodeUtf8, encrypt, MAGIC } from '../public/web/crypto.js';
import { doneTodos, noteTitle, notesByEdited, openTodos, seedNote } from '../public/web/items.js';
import { renderMarkdown } from '../public/web/markdown.js';
import { credentialsReady, objectUrl, regionFor, sign } from '../public/web/s3.js';
import {
	addTicker,
	finished,
	formatPercent,
	formatPosition,
	formatPrice,
	formatSpeed,
	homeRows,
	indexAt,
	parseItunes,
	parseOpml,
	parseRss,
	parseYahooChart,
	parseYahooSearch,
	podcastsOf,
	timestamps,
	watchlist,
} from '../public/web/media.js';
import {
	builtinPage,
	homePreview,
	pagePrompt,
	slashMatches,
	slashResolve,
	typeMode,
} from '../public/web/commands.js';

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
	for (const name of ['index.html', 'app.js', 'sw.js', 'manifest.webmanifest', 'app.css', 'media.js', 'commands.js']) {
		const text = await readFile(new URL(`../public/web/${name}`, import.meta.url), 'utf8');
		assert.ok(text.length > 20, name);
	}
});

test('watchlist add and format match the phone', () => {
	const next = addTicker([], { symbol: 'aapl', name: 'Apple Inc.' });
	assert.equal(next[0].symbol, 'AAPL');
	assert.deepEqual(addTicker(next, { symbol: 'AAPL', name: 'dup' }), next);
	assert.equal(formatPrice(12.3), '$12.30');
	assert.equal(formatPercent(-1.5), '-1.50%');
	assert.deepEqual(watchlist({ watchlist: next }), next);
});

test('yahoo search and chart parse', () => {
	const hits = parseYahooSearch(
		JSON.stringify({ quotes: [{ symbol: 'aapl', shortname: 'Apple Inc.', exchDisp: 'NMS' }] }),
	);
	assert.equal(hits[0].symbol, 'AAPL');
	const quote = parseYahooChart(
		JSON.stringify({
			chart: {
				result: [
					{
						meta: {
							symbol: 'AAPL',
							regularMarketPrice: 110,
							chartPreviousClose: 100,
							shortName: 'Apple',
							currency: 'USD',
						},
						timestamp: [1],
						indicators: { quote: [{ close: [110] }] },
					},
				],
			},
		}),
	);
	assert.equal(quote.changePercent, 10);
	assert.equal(quote.points[0].close, 110);
	assert.equal(indexAt(50, 100, 5), 2);
	assert.equal(formatPosition(0, 62_000), '0:00 of 1:02');
	assert.equal(timestamps('intro 1:02 later')[0].positionMs, 62_000);
	assert.equal(formatSpeed(1.2), '1.2×');
});

test('podcast home rows match phone sections', () => {
	const shows = [{ feedUrl: 'https://x/rss', title: 'Show' }];
	const episodes = [
		{ id: 'old', showId: 'https://x/rss', title: 'Old', pubDate: 1 },
		{ id: 'new', showId: 'https://x/rss', title: 'New', pubDate: 9 },
		{ id: 'mid', showId: 'https://x/rss', title: 'Mid', pubDate: 5 },
	];
	const progress = [
		{ episodeId: 'mid', positionMs: 10, durationMs: 600000, lastPlayedAt: 50, finished: false, skipped: false },
	];
	const rows = homeRows(shows, episodes, progress);
	assert.deepEqual(
		rows.map((row) => row.kind + (row.episode?.id || row.show?.title || row.title)),
		['headerrecent', 'continuemid', 'headernext 5 episodes', 'freshnew', 'freshold', 'headerpodcasts', 'subscriptionShow'],
	);
	assert.equal(finished({ finished: true }), true);
	assert.equal(podcastsOf({}).shows.length, 0);
});

test('rss itunes and opml parse', () => {
	const rss = parseRss(
		`<rss><channel><title>Show</title><itunes:author>Ada</itunes:author>
      <item><title>Ep</title><guid>g1</guid><enclosure url="https://cdn/a.mp3"/><pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate></item>
    </channel></rss>`,
		'https://x/rss',
	);
	assert.equal(rss.show.title, 'Show');
	assert.equal(rss.episodes[0].id, 'g1');
	assert.equal(rss.episodes[0].enclosureUrl, 'https://cdn/a.mp3');
	const itunes = parseItunes(
		JSON.stringify({ results: [{ collectionName: 'Show', artistName: 'Ada', feedUrl: 'https://x/rss' }] }),
	);
	assert.equal(itunes[0].feedUrl, 'https://x/rss');
	const opml = parseOpml(`<opml><body><outline text="Show" xmlUrl="https://x/rss"/></body></opml>`);
	assert.equal(opml[0].feedUrl, 'https://x/rss');
});

test('prefix typeMode and slash match the phone', () => {
	assert.deepEqual(typeMode('>', '-milk'), { prompt: '-', input: 'milk' });
	assert.deepEqual(typeMode('-', 'milk'), { prompt: '-', input: 'milk' });
	assert.equal(pagePrompt('home'), '>');
	assert.equal(pagePrompt('tasks'), '-');
	assert.equal(slashResolve('set').name, 'settings');
	assert.deepEqual(
		slashMatches('').map((row) => row.name),
		['help', 'home', 'notes', 'podcasts', 'pull', 'push', 'settings', 'stocks', 'tasks'],
	);
	assert.equal(builtinPage('notes'), 'notes');
	assert.equal(homePreview([{ kind: 'todo', text: 'a' }, { kind: 'todo', text: 'b', completedAt: 1 }, { kind: 'note', text: 'n' }, { kind: 'todo', text: 'c' }]).map((i) => i.text).join(','), 'a,c');
});

test('mergeDocs unions items and honors deletedIds', async () => {
	const { mergeDocs } = await import('../public/web/merge.js');
	const a = { exportedAt: 2, items: [{ id: '1', text: 'new', updatedAt: 5 }], deletedIds: ['9'] };
	const b = { exportedAt: 1, items: [{ id: '1', text: 'old', updatedAt: 1 }, { id: '2', text: 'keep' }, { id: '9', text: 'gone' }] };
	const merged = mergeDocs(a, b);
	assert.equal(merged.items.find((i) => i.id === '1').text, 'new');
	assert.equal(merged.items.some((i) => i.id === '2'), true);
	assert.equal(merged.items.some((i) => i.id === '9'), false);
});

test('first join keeps local-only data when remote is empty', async () => {
	const { joinDocs, emptyDoc } = await import('../public/web/merge.js');
	const local = {
		exportedAt: 10,
		items: [
			{ id: 'todo-1', kind: 'todo', text: 'buy milk', createdAt: 1, updatedAt: 1 },
			{ id: 'note-1', kind: 'note', text: 'secret', createdAt: 2, updatedAt: 2 },
		],
		watchlist: [{ symbol: 'AAPL', name: 'Apple' }],
		podcasts: { shows: [{ id: 'https://feeds.example/show', feedUrl: 'https://feeds.example/show', title: 'Show' }], episodes: [], progress: [] },
	};
	for (const joined of [joinDocs(local, null), joinDocs(local, emptyDoc(1))]) {
		assert.equal(joined.items.find((i) => i.id === 'todo-1').text, 'buy milk');
		assert.equal(joined.items.find((i) => i.id === 'note-1').text, 'secret');
		assert.equal(joined.watchlist[0].symbol, 'AAPL');
		assert.equal(joined.podcasts.shows[0].title, 'Show');
	}
});

test('first join unions local with existing account or S3 snapshot', async () => {
	const { joinDocs } = await import('../public/web/merge.js');
	const local = {
		exportedAt: 5,
		items: [{ id: 'phone', text: 'on phone', createdAt: 1, updatedAt: 1 }],
		watchlist: [{ symbol: 'TSLA', name: 'Tesla' }],
	};
	const remote = {
		exportedAt: 8,
		items: [{ id: 'cloud', text: 'in cloud', createdAt: 2, updatedAt: 2 }],
		watchlist: [{ symbol: 'AAPL', name: 'Apple' }],
	};
	const joined = joinDocs(local, remote);
	assert.equal(joined.items.some((i) => i.id === 'phone' && i.text === 'on phone'), true);
	assert.equal(joined.items.some((i) => i.id === 'cloud' && i.text === 'in cloud'), true);
	assert.deepEqual(joined.watchlist.map((w) => w.symbol).sort(), ['AAPL', 'TSLA']);
});

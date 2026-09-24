import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { decrypt, encodeUtf8, encrypt, MAGIC } from '../public/web/crypto.js';
import { alignOpenOrder, displayDoneTodos, displayOpenTodos, doneTodos, moveOpenItems, noteTitle, notesByEdited, openTodos, seedNote, taskRank, TASK_COMPLETE_FADE_MS, TASK_COMPLETE_HOLD_MS } from '../public/web/items.js';
import { renderMarkdown } from '../public/web/markdown.js';
import { credentialsReady, objectUrl, regionFor, sign } from '../public/web/s3.js';
import {
	addTicker,
	finished,
	formatPercent,
	formatPosition,
	formatPrice,
	formatSpeed,
	formatYield,
	homeRows,
	indexAt,
	parseItunes,
	parseOpml,
	parseRss,
	parseTimeseries,
	parseYahooChart,
	parseYahooSearch,
	performance,
	podcastsOf,
	quoteStats,
	cagr,
	cagrStats,
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
	webPrefixes,
} from '../public/web/commands.js';
import { homePodcastMark } from '../public/web/media.js';
import {
	aqiLabel,
	compass,
	daySummary,
	displayTemperature,
	FORECAST_CURRENT,
	FORECAST_DAILY,
	FORECAST_HOURLY,
	homeTemperature,
	hourLabel,
	kindOfCode,
	parseAqi,
	parseForecast,
	parseGeocode,
	precipAmount,
	pressure,
	uvLabel,
	wind,
} from '../public/web/weather.js';

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

test('stock detail stats match the phone', () => {
	assert.equal(Number(cagr(100, 121, 2).toFixed(4)), 0.1);
	const year = 365.25 * 86_400;
	const now = 1_800_000_000;
	const points = [
		{ time: now - 10 * year, close: 46.65 },
		{ time: now - 5 * year, close: 75.13 },
		{ time: now - 3 * year, close: 90.96 },
		{ time: now - year, close: 110 },
		{ time: now, close: 121 },
	];
	const row = performance(points, 121, now);
	assert.ok(Math.abs(row.y1 - 10) < 0.2);
	assert.ok(Math.abs(row.y10 - 10) < 0.2);
	const stats = quoteStats({
		open: 100,
		high: 110,
		low: 90,
		volume: 1_000_000,
		pe: 36.61,
		marketCap: 4.67e12,
		eps: 8.74,
		dividendYield: 0.0034,
		beta: 1.09,
		avgVolume: 53_800_000,
		week52High: 260,
		week52Low: 164,
	});
	assert.equal(stats[2].leftLabel, 'P/E');
	assert.equal(stats[2].leftValue, '36.61');
	assert.equal(stats[2].rightValue, '$4.7T');
	assert.equal(stats[3].rightValue, '0.34%');
	assert.equal(stats[4].leftLabel, 'Beta');
	assert.equal(cagrStats(row)[0].leftLabel, '1Y');
	const funds = parseTimeseries(
		JSON.stringify({
			timeseries: {
				result: [
					{ meta: { type: ['trailingPeRatio'] }, trailingPeRatio: [{ reportedValue: { raw: 36.61 } }] },
					{ meta: { type: ['trailingMarketCap'] }, trailingMarketCap: [{ reportedValue: { raw: 4.67e12 } }] },
				],
			},
		}),
	);
	assert.equal(funds.pe, 36.61);
	assert.equal(funds.marketCap, 4.67e12);
	assert.equal(formatYield(0.0034), '0.34%');
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
	assert.equal(pagePrompt('settings'), '>');
	assert.equal(pagePrompt('help'), '>');
	assert.equal(pagePrompt('weather'), '>');
	assert.equal(pagePrompt('chat'), '?');
	assert.equal(pagePrompt('chats'), '?');
	assert.equal(slashResolve('set').name, 'settings');
	assert.deepEqual(
		slashMatches('').map((row) => row.name),
		['help', 'home', 'notes', 'podcasts', 'pull', 'push', 'settings', 'stocks', 'tasks', 'weather'],
	);
	assert.equal(builtinPage('notes'), 'notes');
	assert.equal(builtinPage('weather'), 'weather');
	assert.deepEqual(webPrefixes().map((row) => row.glyph), ['-', '+', '$', '?', '/']);
	assert.equal(webPrefixes().some((row) => row.glyph === '*'), false);
	assert.equal(homePreview([{ kind: 'todo', text: 'a' }, { kind: 'todo', text: 'b', completedAt: 1 }, { kind: 'note', text: 'n' }, { kind: 'todo', text: 'c' }]).map((i) => i.text).join(','), 'a,c');
});

test('ai chat plan uses the synced key and refuses a LAN host', async () => {
	const { chatPlan, chatTitle, questionFromInput, threadsOf, bearerOf } = await import('../public/web/ai.js');
	assert.equal(questionFromInput('?  what time'), 'what time');
	assert.equal(chatTitle([{ role: 'user', content: 'first line\nsecond' }]), 'first line');
	assert.deepEqual(
		threadsOf([{ id: 'a', updatedAt: 1, messages: [{ role: 'user', content: 'old' }] }, { id: 'b', updatedAt: 2, messages: [] }]).map((row) => row.id),
		['a'],
	);
	const plan = chatPlan(
		{ provider: 'XAI', apiKey: 'sk-test' },
		[{ role: 'user', content: 'hi' }],
		1_000,
	);
	assert.equal(plan.base, 'https://api.x.ai/v1');
	assert.equal(plan.bearer, 'sk-test');
	assert.equal(plan.oauth, false);
	const signed = chatPlan(
		{ provider: 'XAI', oauthAccess: 'oauth-tok', oauthExpiresAtEpochMs: 400_000 },
		[{ role: 'user', content: 'hi' }],
		1_000,
	);
	assert.equal(bearerOf({ oauthAccess: 'oauth-tok', oauthExpiresAtEpochMs: 400_000 }, 1_000), 'oauth-tok');
	assert.equal(signed.oauth, true);
	assert.match(chatPlan({ provider: 'HERMES', hermesWebUrl: 'http://192.168.1.10:9119' }, [{ role: 'user', content: 'hi' }]).error, /LAN/);
	assert.match(chatPlan({ provider: 'OPENAI' }, [{ role: 'user', content: 'hi' }]).error, /API key/);
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

test('mergeDocs drops an alarm deleted on either side', async () => {
	const { mergeDocs } = await import('../public/web/merge.js');
	const gone = { id: 'a-gone', hour: 7, minute: 30 };
	const keep = { id: 'a-keep', hour: 8, minute: 0 };
	const phone = { exportedAt: 20, alarms: [keep], deletedAlarmIds: ['a-gone'] };
	const cloud = { exportedAt: 10, alarms: [gone, keep] };
	for (const merged of [mergeDocs(phone, cloud), mergeDocs(cloud, phone)]) {
		assert.equal(merged.alarms.some((alarm) => alarm.id === 'a-gone'), false);
		assert.equal(merged.alarms.some((alarm) => alarm.id === 'a-keep'), true);
		assert.equal(merged.deletedAlarmIds.includes('a-gone'), true);
	}
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

test('slimDoc keeps subscriptions and played progress, not catalogs or quotes', async () => {
	const { slimDoc } = await import('../public/web/merge.js');
	const html = 'd'.repeat(100_000);
	const fat = {
		exportedAt: 1,
		watchlist: [{ symbol: 'AAPL', name: 'Apple', addedAt: 1, price: 190, changePercent: 1.2, previousClose: 188 }],
		podcasts: {
			shows: [{ feedUrl: 'https://feeds.example/show', title: 'Show' }],
			episodes: Array.from({ length: 50 }, (_, i) => ({
				id: `ep-${i}`,
				showId: 'https://feeds.example/show',
				title: `Ep ${i}`,
				enclosureUrl: `https://cdn.example/${i}.mp3`,
				description: html,
			})),
			progress: [
				{ episodeId: 'ep-1', positionMs: 12000, lastPlayedAt: 9 },
				{ episodeId: 'ep-2', positionMs: 0, lastPlayedAt: 0 },
			],
		},
	};
	const full = JSON.stringify(fat);
	const slim = slimDoc(fat);
	assert.equal(full.length > 4_000_000, true);
	assert.equal(JSON.stringify(slim).length < 2_000, true);
	assert.deepEqual(slim.podcasts.episodes, []);
	assert.deepEqual(slim.podcasts.progress.map((row) => row.episodeId), ['ep-1']);
	assert.equal(slim.podcasts.shows[0].title, 'Show');
	assert.equal(slim.watchlist[0].symbol, 'AAPL');
	assert.equal(slim.watchlist[0].price, undefined);
});

test('writeSnapshot stores a slim copy and recovers from Safari quota', async () => {
	const { writeSnapshot, isQuotaError } = await import('../public/web/merge.js');
	assert.equal(isQuotaError({ name: 'QuotaExceededError', message: 'The quota has been exceeded.' }), true);
	const fat = {
		podcasts: {
			shows: [{ feedUrl: 'https://feeds.example/show', title: 'Show' }],
			episodes: [{ id: 'ep-1', showId: 'https://feeds.example/show', title: 'Ep', description: 'd'.repeat(1000) }],
			progress: [{ episodeId: 'ep-1', lastPlayedAt: 9 }],
		},
	};
	const store = new Map();
	let throws = 1;
	const storage = {
		setItem(key, value) {
			if (throws > 0) {
				throws -= 1;
				const err = new Error('The quota has been exceeded.');
				err.name = 'QuotaExceededError';
				err.code = 22;
				throw err;
			}
			store.set(key, value);
		},
		removeItem(key) {
			store.delete(key);
		},
	};
	assert.equal(writeSnapshot(storage, 'snap', fat), true);
	const saved = JSON.parse(store.get('snap'));
	assert.deepEqual(saved.podcasts.episodes, []);
	assert.equal(saved.podcasts.shows[0].title, 'Show');
	assert.equal(saved.podcasts.progress[0].episodeId, 'ep-1');
});

test('slim account snapshots need RSS hydrate for every subscribed show', async () => {
	const { slimDoc } = await import('../public/web/merge.js');
	const { showsNeedingFeed, podcastsOf } = await import('../public/web/media.js');
	const fat = {
		podcasts: {
			shows: [
				{ feedUrl: 'https://feeds.example/a', title: 'A' },
				{ feedUrl: 'https://feeds.example/b', title: 'B' },
			],
			episodes: [
				{ id: 'ep-a', showId: 'https://feeds.example/a', title: 'Ep A' },
				{ id: 'ep-b', showId: 'https://feeds.example/b', title: 'Ep B' },
			],
			progress: [],
		},
	};
	const slim = slimDoc(fat);
	assert.deepEqual(
		showsNeedingFeed(podcastsOf(slim)).map((show) => show.feedUrl),
		['https://feeds.example/a', 'https://feeds.example/b'],
	);
});

test('shows with cached episodes stay stale after the refresh window', async () => {
	const { showsNeedingFeed, podcastsOf, FEED_STALE_MS } = await import('../public/web/media.js');
	const now = Date.now();
	const doc = {
		podcasts: {
			shows: [
				{ feedUrl: 'https://feeds.example/fresh', title: 'F', lastCheckedAt: now - 60_000 },
				{ feedUrl: 'https://feeds.example/stale', title: 'S', lastCheckedAt: now - FEED_STALE_MS - 1 },
				{ feedUrl: 'https://feeds.example/never', title: 'N' },
			],
			episodes: [
				{ id: 'ep-f', showId: 'https://feeds.example/fresh', title: 'Ep F' },
				{ id: 'ep-s', showId: 'https://feeds.example/stale', title: 'Ep S' },
			],
			progress: [],
		},
	};
	assert.deepEqual(
		showsNeedingFeed(podcastsOf(doc), now).map((show) => show.feedUrl),
		['https://feeds.example/stale', 'https://feeds.example/never'],
	);
	// exportedAt is not a feed check. A just-exported snapshot still refreshes
	// a show that has episodes but has never been checked.
	const bag = podcastsOf({ ...doc, exportedAt: now });
	delete bag.shows[0].lastCheckedAt;
	assert.equal(bag.exportedAt, undefined);
	assert.deepEqual(
		showsNeedingFeed(bag, now).map((show) => show.feedUrl),
		['https://feeds.example/fresh', 'https://feeds.example/stale', 'https://feeds.example/never'],
	);
});

test('a failed feed does not discard shows that parsed', async () => {
	const { applyCheckedFeeds } = await import('../public/web/media.js');
	const shows = [
		{ feedUrl: 'https://feeds.example/ok', title: 'Ok', lastCheckedAt: 1 },
		{ feedUrl: 'https://feeds.example/bad', title: 'Bad', lastCheckedAt: 1 },
	];
	const episodes = [
		{ id: 'old-ok', showId: 'https://feeds.example/ok', title: 'Old' },
		{ id: 'old-bad', showId: 'https://feeds.example/bad', title: 'Keep' },
	];
	const applied = applyCheckedFeeds(shows, episodes, [
		{
			feedUrl: 'https://feeds.example/ok',
			feed: {
				show: { feedUrl: 'https://feeds.example/ok', title: 'Ok' },
				episodes: [{ id: 'new-ok', showId: 'https://feeds.example/ok', title: 'New' }],
			},
		},
		{ feedUrl: 'https://feeds.example/bad', feed: null },
	], 50);
	assert.deepEqual(applied.failed, ['https://feeds.example/bad']);
	assert.equal(applied.shows[0].lastCheckedAt, 50);
	assert.equal(applied.shows[1].lastCheckedAt, 1);
	assert.equal(applied.shows[1], shows[1]);
	assert.deepEqual(applied.episodes.map((episode) => episode.id), ['old-bad', 'new-ok']);
});

test('opening a show refreshes through go and paints the show screen', async () => {
	const js = await readFile(new URL('../public/web/app.js', import.meta.url), 'utf8');
	assert.match(js, /state\.showId = show\.feedUrl;\s*go\('show'\)/);
	assert.match(js, /state\.tab === 'pods' \|\| state\.tab === 'show'/);
	assert.match(js, /Could not refresh this feed/);
	assert.doesNotMatch(js, /Pull to refresh the feeds/);
});

test('mergeDocs keeps local show notes when the cloud copy omitted them', async () => {
	const { mergeDocs } = await import('../public/web/merge.js');
	const html = '<p>show notes</p>';
	const merged = mergeDocs(
		{
			exportedAt: 10,
			podcasts: { episodes: [{ id: 'ep-1', title: 'Ep', pubDate: 5, description: html }] },
		},
		{
			exportedAt: 11,
			podcasts: { episodes: [{ id: 'ep-1', title: 'Ep', pubDate: 5, description: '' }] },
		},
	);
	assert.equal(merged.podcasts.episodes[0].description, html);
});

test('overwrite warning is confirmed before login or S3 pull', async () => {
	const { OVERWRITE_WARNING, confirmOverwriteLocal } = await import('../public/web/account.js');
	assert.match(OVERWRITE_WARNING, /overwrite any local data/);
	assert.equal(confirmOverwriteLocal(() => true), true);
	assert.equal(confirmOverwriteLocal(() => false), false);
});

test('weather parse and home temperature match the phone', () => {
	const places = parseGeocode(JSON.stringify({
		results: [{ name: 'Kitchener', latitude: 43.45, longitude: -80.49, admin1: 'Ontario', country: 'Canada' }],
	}));
	assert.equal(places[0].label, 'Kitchener, Ontario, Canada');
	const fetched = Date.parse('2026-09-08T16:00:00Z');
	const forecast = parseForecast(JSON.stringify({
		latitude: 43.45,
		longitude: -80.49,
		timezone: 'America/Toronto',
		current: {
			temperature_2m: 18.2,
			apparent_temperature: 16.4,
			weather_code: 3,
			relative_humidity_2m: 64,
			precipitation: 0.0,
			wind_speed_10m: 12.4,
			wind_direction_10m: 270,
			wind_gusts_10m: 22.0,
			surface_pressure: 1013.2,
			visibility: 24100,
			cloud_cover: 80,
			is_day: 1,
			dew_point_2m: 11.1,
		},
		hourly: {
			time: ['2026-09-08T12:00', '2026-09-08T13:00', '2026-09-08T22:00'],
			temperature_2m: [18.2, 19.0, 12.0],
			weather_code: [3, 61, 0],
			precipitation_probability: [40, 70, 5],
			uv_index: [4.2, 5.1, 0.0],
			is_day: [1, 1, 0],
		},
		daily: {
			time: ['2026-09-08', '2026-09-09'],
			weather_code: [3, 61],
			temperature_2m_max: [22.1, 18.0],
			temperature_2m_min: [11.0, 10.0],
			precipitation_probability_max: [40, 80],
			precipitation_sum: [1.2, 8.4],
			sunrise: ['2026-09-08T06:42', '2026-09-09T06:43'],
			sunset: ['2026-09-08T19:51', '2026-09-09T19:49'],
			uv_index_max: [5.4, 3.1],
		},
	}), fetched, 42);
	assert.equal(forecast.current.temperatureC, 18);
	assert.equal(forecast.current.feelsC, 16);
	assert.equal(forecast.current.humidity, 64);
	assert.equal(forecast.current.precipProb, 40);
	assert.equal(forecast.current.windDir, 270);
	assert.equal(forecast.hourly.length, 3);
	assert.equal(forecast.hourly[0].isDay, true);
	assert.equal(forecast.hourly[2].isDay, false);
	assert.equal(forecast.daily[0].precipMm, 1.2);
	assert.equal(forecast.daily[0].sunrise, '06:42');
	assert.equal(forecast.aqi, 42);
	assert.equal(kindOfCode(3), 'CLOUDY');
	assert.equal(homeTemperature(12, 'METRIC'), '12°');
	assert.equal(displayTemperature(12, 'IMPERIAL'), 54);
	assert.equal(hourLabel(fetched, 'America/Toronto'), '12');
	assert.equal(compass(270), 'W');
	assert.equal(uvLabel(4.2), '4 mod');
	assert.equal(aqiLabel(42), '42 good');
	assert.equal(wind(12.4, 270, 'METRIC'), '12 km/h W');
	assert.match(pressure(1013.2, 'METRIC'), /hPa/);
	assert.equal(precipAmount(1.2, 'METRIC'), '1.2 mm');
	assert.equal(precipAmount(8.4, 'IMPERIAL'), '0.33 in');
	assert.equal(daySummary({ date: '2026-09-08', code: 3, highC: 22, lowC: 11, precipProb: 40, precipMm: 1.2 }, 'METRIC'), '40% · 1.2 mm');
	assert.equal(daySummary({ date: '2026-09-09', code: 61, highC: 18, lowC: 10, precipProb: 80, precipMm: 8.4 }, 'METRIC'), '80% · 8.4 mm');
	assert.equal(daySummary({ date: '2026-09-10', code: 0, highC: 24, lowC: 12, precipProb: 10, uv: 8.2, precipMm: 0 }, 'METRIC'), 'clear · UV 8');
	assert.equal(daySummary({ date: '2026-09-13', code: 95, highC: 17, lowC: 11, precipProb: 70, precipMm: 12 }, 'METRIC'), 'storms · 12 mm');
	assert.equal(daySummary({ date: '2026-09-12', code: 45, highC: 19, lowC: 12, precipProb: 10, precipMm: 0 }, 'METRIC'), 'fog');
	assert.equal(parseAqi(JSON.stringify({ current: { us_aqi: 42, european_aqi: 18 } })), 42);
});

test('weather screen layout matches the phone', async () => {
	const js = await readFile(new URL('../public/web/app.js', import.meta.url), 'utf8');
	const css = await readFile(new URL('../public/web/app.css', import.meta.url), 'utf8');
	const worker = await readFile(new URL('../../cloudflare/src/index.ts', import.meta.url), 'utf8');
	assert.match(js, /weather-hero/);
	assert.match(js, /H \$\{temp\(today\.highC, units\)\}  L \$\{temp\(today\.lowC, units\)\}/);
	assert.match(js, /weatherStat\('Feels'/);
	assert.match(js, /weatherStat\('Precip'/);
	assert.match(js, /weatherStat\('Wind'/);
	assert.match(js, /weather-hours/);
	assert.match(js, /\['Humidity'/);
	assert.match(js, /\['Dew point'/);
	assert.match(js, /\['UV index'/);
	assert.match(js, /\['Pressure'/);
	assert.match(js, /\['Visibility'/);
	assert.match(js, /\['Cloud cover'/);
	assert.match(js, /\['Sunrise'/);
	assert.match(js, /\['Sunset'/);
	assert.match(js, /\['Gusts'/);
	assert.match(js, /\['Air quality'/);
	assert.match(js, /\/api\/weather\/air\?/);
	assert.match(css, /\.weather-hero/);
	assert.match(css, /\.weather-hours/);
	assert.match(css, /\.weather-details/);
	assert.match(worker, /\/api\/weather\/air/);
	assert.match(worker, new RegExp(FORECAST_CURRENT.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
	assert.match(worker, new RegExp(FORECAST_HOURLY.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
	assert.match(worker, new RegExp(FORECAST_DAILY.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
	assert.match(worker, /air-quality-api\.open-meteo\.com/);
});

test('home podcast mark matches the phone', () => {
	assert.equal(homePodcastMark(false, false, null), 'HEADPHONES');
	assert.equal(homePodcastMark(true, true, null), 'PAUSE');
	assert.equal(homePodcastMark(false, true, 1000), 'PLAY');
	assert.equal(homePodcastMark(false, true, 8000), 'HEADPHONES');
});

test('command bar enter key is go so iOS Return submits a todo', async () => {
	const text = await readFile(new URL('../public/web/app.js', import.meta.url), 'utf8');
	assert.match(text, /input\.enterKeyHint = promptEnterHint\(\)/);
	assert.match(text, /function promptEnterHint\(\) \{\s*return 'go';\s*\}/);
	assert.doesNotMatch(text, /return 'done'/);
});

test('command bar shows a tappable check or send on the right', async () => {
	const js = await readFile(new URL('../public/web/app.js', import.meta.url), 'utf8');
	const css = await readFile(new URL('../public/web/app.css', import.meta.url), 'utf8');
	assert.match(js, /function commandSubmitKind\(prompt\) \{\s*return prompt === '-' \? 'check' : 'send';\s*\}/);
	assert.match(js, /submit\.className = 'command-submit'/);
	assert.match(js, /commandSubmitIcon\(commandSubmitKind\(state\.prompt\)\)/);
	assert.match(js, /save task/);
	assert.match(js, /'✓'/);
	assert.match(css, /\.command-submit \{[^}]*min-width:\s*2\.6rem/s);
	assert.match(css, /\.command-bar \{[^}]*grid-template-columns:\s*auto minmax\(0, 1fr\) auto/s);
	assert.doesNotMatch(css, /\.command-submit \{[^}]*clip-path:\s*inset\(50%\)/s);
});

test('command dock keeps extra bottom space on iPhone standalone PWA', async () => {
	const css = await readFile(new URL('../public/web/app.css', import.meta.url), 'utf8');
	const sw = await readFile(new URL('../public/web/sw.js', import.meta.url), 'utf8');
	assert.match(
		css,
		/@media \(max-width: 48rem\) \{[\s\S]*?padding-bottom:\s*max\(2\.25rem, calc\(1\.1rem \+ env\(safe-area-inset-bottom, 0px\)\)\)/,
	);
	assert.match(
		css,
		/@media \(display-mode: standalone\) \{\s*\.command-dock \{\s*padding-bottom:\s*max\(2\.75rem, calc\(1\.5rem \+ env\(safe-area-inset-bottom, 0px\)\)\)/s,
	);
	assert.match(sw, /builder-launcher-web-v26/);
});

test('list rows stack title over subtitle so long show names cannot crush the title', async () => {
	const js = await readFile(new URL('../public/web/app.js', import.meta.url), 'utf8');
	const css = await readFile(new URL('../public/web/app.css', import.meta.url), 'utf8');
	assert.match(js, /function listRow\(/);
	assert.match(js, /copy\.className = 'copy'/);
	assert.match(js, /subtitle: show\?\.title \|\| ''/);
	assert.doesNotMatch(js, /join\(' · '\)/);
	assert.match(css, /\.row \.copy \{[^}]*min-width:\s*0/s);
	assert.match(css, /\.row \.copy \.body \{[^}]*-webkit-line-clamp:\s*3/s);
	assert.match(css, /\.row \.body \{[^}]*word-break:\s*normal/s);
	assert.doesNotMatch(css, /word-break:\s*break-word/);
});

test('PWA chrome uses a gear, auto-sync, now playing, and Android-style tasks', async () => {
	const js = await readFile(new URL('../public/web/app.js', import.meta.url), 'utf8');
	const css = await readFile(new URL('../public/web/app.css', import.meta.url), 'utf8');
	assert.match(js, /function gearButton\(/);
	assert.match(js, /aria-label', 'settings'/);
	assert.doesNotMatch(js, /push\*' : 'push'/);
	assert.match(js, /function autoSyncCard\(/);
	assert.match(js, /id: 'save', label: 'on save'/);
	assert.match(js, /function maybeSyncSave\(/);
	assert.match(js, /function nowPlayingBar\(/);
	assert.match(js, /label\.textContent = 'now playing'/);
	assert.match(js, /className = 'body todo-text'/);
	assert.match(js, /function taskCheckButton\(/);
	assert.match(js, /aria-label', done \? 'reopen task' : 'complete task'/);
	assert.doesNotMatch(js, /iconButton\('edit task'/);
	assert.match(js, /iconButton\('delete task'/);
	assert.match(js, /iconButton\('move task up'/);
	assert.match(js, /function attachTaskDrag\(/);
	assert.match(css, /\.task-move \{/);
	assert.match(css, /\.row \.task-check \{/);
	assert.match(js, /function updateTodoText\(/);
	assert.doesNotMatch(js, /mark\.textContent = done \? '×' : '·'/);
	assert.match(css, /\.now-playing \{/);
	assert.match(css, /\.ghost\.gear/);
	assert.doesNotMatch(js, /M9 1\.5v2\.1/);
	assert.match(js, /if \(state\.tab === 'home'\) app\.append\(main\(\), commandDock\(\)\)/);
	assert.doesNotMatch(js, /el\.append\(title\(''\)\);/);
	assert.match(js, /cluster\.append\(analogClock\(\), statusLine\('clock-status'\)\)/);
	assert.match(js, /right\.append\(gearButton\(\)\)/);
	assert.match(css, /\.clock-status \{/);
	assert.match(css, /\.podcast-mark svg \{[^}]*width:\s*18px/s);
	assert.match(css, /main\.home-main \{/);
	assert.match(js, /innerHTML = '<svg[^>]*><path d="M7\.2 1\.6/);
});

test('mobile shell pins home to the top and follows the visual viewport', async () => {
	const js = await readFile(new URL('../public/web/app.js', import.meta.url), 'utf8');
	const css = await readFile(new URL('../public/web/app.css', import.meta.url), 'utf8');
	const html = await readFile(new URL('../public/web/index.html', import.meta.url), 'utf8');
	assert.match(js, /function pinVisualViewport\(/);
	assert.match(js, /pinVisualViewport\(\)/);
	assert.match(js, /input\.focus\(\{ preventScroll: true \}\)/);
	assert.match(css, /\.home \{[^}]*justify-content:\s*flex-start/s);
	assert.match(css, /#app \{[^}]*height:\s*var\(--vv-height/s);
	assert.match(css, /html,\s*body \{[^}]*overflow:\s*hidden/s);
	assert.match(html, /interactive-widget=resizes-content/);
});

test('checking a task keeps list scroll instead of jumping to the top', async () => {
	const js = await readFile(new URL('../public/web/app.js', import.meta.url), 'utf8');
	assert.match(js, /listTab === state\.tab \? list\.scrollTop : 0/);
	assert.match(js, /next\.scrollTop = restore/);
	assert.match(js, /function noFocusScroll\(el\) \{/);
	assert.match(js, /pointerdown', \(event\) => event\.preventDefault\(\)/);
	assert.match(js, /noFocusScroll\(el\)/);
	assert.match(js, /noFocusScroll\(body\)/);
	assert.match(js, /tag === 'INPUT' \|\| tag === 'TEXTAREA'/);
	assert.doesNotMatch(js, /addEventListener\('focusin', \(\) => \{\s*apply\(\);\s*window\.scrollTo\(0, 0\);/s);
});

test('completing a task holds strikethrough then fades into done', async () => {
	const items = [
		{ id: 'a', kind: 'todo', text: 'a', completedAt: null },
		{ id: 'b', kind: 'todo', text: 'b', completedAt: 2 },
		{ id: 'c', kind: 'todo', text: 'c', completedAt: null },
		{ id: 'd', kind: 'todo', text: 'd', completedAt: 3 },
	];
	const pending = new Map([['b', 1]]);
	assert.deepEqual(displayOpenTodos(items, pending).map((row) => row.text), ['a', 'b', 'c']);
	assert.deepEqual(displayDoneTodos(items, pending).map((row) => row.text), ['d']);
	assert.equal(TASK_COMPLETE_HOLD_MS, 1000);
	assert.equal(TASK_COMPLETE_FADE_MS, 280);
	const js = await readFile(new URL('../public/web/app.js', import.meta.url), 'utf8');
	const css = await readFile(new URL('../public/web/app.css', import.meta.url), 'utf8');
	assert.match(js, /function beginCompleteAnim\(/);
	assert.match(js, /row\.classList\.add\('task-leaving'\)/);
	assert.match(js, /enteringComplete\.add\(id\)/);
	assert.match(css, /\.row\.task-leaving \{/);
	assert.match(css, /@keyframes task-leave/);
	assert.match(css, /@keyframes task-enter/);
});

test('settings and help do not open the slash list by themselves', async () => {
	const js = await readFile(new URL('../public/web/app.js', import.meta.url), 'utf8');
	assert.match(js, /else if \(state\.menu === 'slash'\) dock\.append\(slashMenu\(\)\)/);
	assert.doesNotMatch(js, /state\.prompt === '\/' \|\| state\.menu === 'slash'/);
	assert.match(js, /state\.prompt = DEFAULT_PROMPT/);
	assert.doesNotMatch(js, /state\.tab = 'settings';\s*state\.prompt = '\/'/s);
	assert.match(js, /await pull\(\{ overwrite: mode === 'login' \}\)/);
	assert.doesNotMatch(js, /overwrite: mode === 'login', quiet: true/);
});

test('account API calls abort after 20s', async () => {
	const { API_TIMEOUT_MS, isAbortError } = await import('../public/web/account.js');
	const js = await readFile(new URL('../public/web/account.js', import.meta.url), 'utf8');
	assert.equal(API_TIMEOUT_MS, 20_000);
	assert.equal(isAbortError({ name: 'TimeoutError' }), true);
	assert.equal(isAbortError({ name: 'AbortError' }), true);
	assert.equal(isAbortError({ name: 'TypeError' }), false);
	assert.match(js, /timeout = API_TIMEOUT_MS/);
	assert.match(js, /AbortSignal\.timeout\(timeout\)/);
	assert.match(js, /Request timed out\. Try again\./);
});

test('mergeDocs keeps different tasks from both sides', async () => {
	const { mergeDocs } = await import('../public/web/merge.js');
	const merged = mergeDocs(
		{ exportedAt: 20, items: [{ id: 'phone', text: 'from phone', updatedAt: 20 }] },
		{ exportedAt: 10, items: [{ id: 'web', text: 'from web', updatedAt: 10 }] },
	);
	assert.equal(merged.items.length, 2);
	assert.equal(merged.items.find((i) => i.id === 'phone').text, 'from phone');
	assert.equal(merged.items.find((i) => i.id === 'web').text, 'from web');
	const clash = mergeDocs(
		{ exportedAt: 1, items: [{ id: 'same', text: 'older edit', updatedAt: 5 }] },
		{ exportedAt: 2, items: [{ id: 'same', text: 'newer edit', updatedAt: 9 }] },
	);
	assert.equal(clash.items.length, 1);
	assert.equal(clash.items[0].text, 'newer edit');
	assert.deepEqual(
		mergeDocs(
			{
				exportedAt: 20,
				items: [
					{ id: 'a', kind: 'todo', text: 'alpha', createdAt: 100, order: 599, orderedAt: 600 },
					{ id: 'b', kind: 'todo', text: 'beta', createdAt: 200, order: 598, orderedAt: 600 },
				],
			},
			{
				exportedAt: 10,
				items: [
					{ id: 'a', kind: 'todo', text: 'alpha edited', createdAt: 100, updatedAt: 700 },
					{ id: 'b', kind: 'todo', text: 'beta', createdAt: 200 },
					{ id: 'c', kind: 'todo', text: 'from web', createdAt: 400 },
				],
			},
		).items.map((item) => item.text),
		['from web', 'alpha edited', 'beta'],
	);
});

test('open todos sort newest first unless manually moved lower', () => {
	const appended = [
		{ id: 'old', kind: 'todo', text: 'older', createdAt: 10 },
		{ id: 'new', kind: 'todo', text: 'newer', createdAt: 40 },
	];
	assert.deepEqual(openTodos(appended).map((item) => item.text), ['newer', 'older']);
	const moved = moveOpenItems(
		[
			{ id: 'top', kind: 'todo', text: 'top', createdAt: 30 },
			{ id: 'mid', kind: 'todo', text: 'mid', createdAt: 20 },
			{ id: 'low', kind: 'todo', text: 'low', createdAt: 10 },
		],
		0,
		2,
		100,
	);
	assert.deepEqual(openTodos(moved).map((item) => item.text), ['mid', 'low', 'top']);
	assert.ok(moved.filter((item) => !item.completedAt).every((item) => item.orderedAt === 100 && item.order < 100));
	const added = [...moved, { id: 'fresh', kind: 'todo', text: 'fresh', createdAt: 100 }];
	assert.deepEqual(openTodos(added).map((item) => item.text), ['fresh', 'mid', 'low', 'top']);
	assert.equal(taskRank({ createdAt: 5, order: 9 }), 9);
	assert.deepEqual(
		alignOpenOrder(
			[
				{ id: 'a', kind: 'todo', createdAt: 10, order: 50, orderedAt: 60 },
				{ id: 'b', kind: 'todo', createdAt: 5, order: 49, orderedAt: 60 },
			],
			[
				{ id: 'a', kind: 'todo', createdAt: 10 },
				{ id: 'web', kind: 'todo', text: 'web', createdAt: 20 },
			],
			[
				{ id: 'a', kind: 'todo', createdAt: 10, order: 50, orderedAt: 60 },
				{ id: 'b', kind: 'todo', createdAt: 5, order: 49, orderedAt: 60 },
				{ id: 'web', kind: 'todo', text: 'web', createdAt: 20 },
			],
		).map((item) => item.id),
		['web', 'a', 'b'],
	);
});

import assert from 'node:assert/strict';
import test from 'node:test';
import {
	addArticle,
	articleId,
	chunks,
	durationMs,
	extractArticle,
	fnv1a64,
	indexFromInput,
	mergeReads,
	normalizeUrl,
	positionLabel,
	publicationFromUrl,
} from '../public/web/reads.js';

test('read articles extract, order, and match the phone hash', () => {
	assert.equal(fnv1a64('https://example.com/a'), '046a4934057f51cf');
	assert.equal(articleId('https://example.com/a'), 'r046a4934057f51cf');
	assert.equal(articleId('HTTPS://Example.com/a/'), 'r046a4934057f51cf');
	assert.equal(normalizeUrl('nytimes.com/story'), 'https://nytimes.com/story');
	assert.equal(normalizeUrl('1'), null);
	assert.equal(normalizeUrl('https://10.1.2.3/a'), null);
	assert.equal(publicationFromUrl('https://www.nytimes.com/story'), 'nytimes.com');
	const html = `<!doctype html><html><head><title>Browser title</title>
		<meta property="og:title" content="The Real Title">
		<meta property="og:site_name" content="The Atlantic"></head><body>
		<nav>Home About</nav><article><p>First paragraph of the piece.</p>
		<p>Second paragraph continues the argument.</p>
		<script>var secret = "do not read";</script></article>
		<footer>Copyright 2026</footer></body></html>`;
	const article = extractArticle(html, 'https://www.theatlantic.com/story');
	assert.equal(article.title, 'The Real Title');
	assert.equal(article.publication, 'The Atlantic');
	assert.match(article.text, /First paragraph of the piece/);
	assert.doesNotMatch(article.text, /Home About|do not read|Copyright/);
	assert.equal(durationMs(Array.from({ length: 160 }, () => 'word').join(' ')), 60_000);
	assert.equal(positionLabel(0, 60_000), '0:00 of 1:00');
	assert.equal(indexFromInput('1', 3), 0);
	assert.equal(indexFromInput('4', 3), null);
	const added = addArticle(
		[{ id: 'old', addedAt: 1, title: 'old', publication: 'E', text: 'body', url: 'https://e/old' }],
		{ id: 'new', title: 'new', publication: 'E', text: 'body', url: 'https://e/new' },
		9,
	);
	assert.deepEqual(
		added.articles.map((item) => item.id),
		['new', 'old'],
	);
	const merged = mergeReads(
		{
			articles: [{ id: 'r1', title: 'Old', text: 'short text that is still long enough to keep', addedAt: 1, url: 'https://e/r1', publication: 'E' }],
			progress: [{ articleId: 'r1', positionMs: 1000, updatedAt: 5 }],
		},
		{
			articles: [{ id: 'r1', title: 'New', text: 'a much longer body that should win because it was added later and has more words', addedAt: 8, url: 'https://e/r1', publication: 'E' }],
			progress: [{ articleId: 'r1', positionMs: 200, updatedAt: 4 }],
			deletedIds: ['gone'],
		},
	);
	assert.equal(merged.articles[0].title, 'New');
	assert.match(merged.articles[0].text, /^a much longer/);
	assert.equal(merged.progress[0].positionMs, 1000);
	const text = 'First sentence here. Second sentence follows.';
	const rows = chunks(text);
	assert.equal(text.slice(rows[0].start, rows[0].end), 'First sentence here.');
	assert.equal(rows.length, 2);
});

test('account and S3 slim copies keep the article and its position', async () => {
	const { slimDoc, mergeDocs } = await import('../public/web/merge.js');
	const body = 'The full article body that must survive sync. '.repeat(8);
	const phone = {
		exportedAt: 20,
		reads: {
			articles: [{ id: 'r1', url: 'https://example.com/a', title: 'Title', publication: 'Example', text: body, addedAt: 9 }],
			progress: [{ articleId: 'r1', positionMs: 90_000, updatedAt: 12 }],
			deletedIds: [],
		},
	};
	const slim = slimDoc(phone);
	assert.equal(slim.reads.articles[0].text, body);
	assert.equal(slim.reads.progress[0].positionMs, 90_000);
	const cloud = {
		exportedAt: 10,
		reads: {
			articles: [{ id: 'r1', url: 'https://example.com/a', title: 'Old', publication: 'Example', text: 'short', addedAt: 1 }],
			progress: [{ articleId: 'r1', positionMs: 1000, updatedAt: 4 }],
		},
	};
	for (const merged of [mergeDocs(phone, cloud), mergeDocs(cloud, phone)]) {
		assert.equal(merged.reads.articles[0].text, body);
		assert.equal(merged.reads.progress[0].positionMs, 90_000);
	}
	const slimmed = slimDoc(mergeDocs(phone, cloud));
	assert.equal(slimmed.reads.articles[0].text, body);
	assert.equal(slimmed.reads.progress[0].positionMs, 90_000);
});

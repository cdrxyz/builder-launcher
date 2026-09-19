import assert from 'node:assert/strict';
import test from 'node:test';
import { fyydToItunes } from '../src/podcasts.ts';
import { isFyydHost } from '../src/safe.ts';

test('fyydToItunes maps xmlURL to feedUrl', () => {
	const mapped = JSON.parse(
		fyydToItunes(
			JSON.stringify({
				data: [
					{
						title: 'Serial',
						author: 'Serial Productions',
						xmlURL: 'https://feeds.example/serial',
						imgURL: 'https://img.example/a.jpg',
					},
					{ title: 'No feed' },
				],
			}),
		),
	);
	assert.equal(mapped.resultCount, 1);
	assert.equal(mapped.results[0].collectionName, 'Serial');
	assert.equal(mapped.results[0].feedUrl, 'https://feeds.example/serial');
	assert.equal(mapped.results[0].artworkUrl600, 'https://img.example/a.jpg');
});

test('fyyd host allowlist', () => {
	assert.equal(isFyydHost('api.fyyd.de'), true);
	assert.equal(isFyydHost('evil.example'), false);
});

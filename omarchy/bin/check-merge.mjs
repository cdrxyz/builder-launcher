#!/usr/bin/env node
// Regenerates tests/fixtures/merge.json from the web merge modules and checks
// the committed file still matches. The C++ port is tested against that file.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { mergeDocs, slimDoc, emptyDoc } from '../../website/public/web/merge.js';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const fixturePath = join(root, 'tests/fixtures/merge.json');

const T0 = 1_760_000_000_000;
const T1 = T0 + 86_400_000;
const T2 = T1 + 43_200_000;

function task(over) {
	return { id: 'abc', kind: 'todo', text: 'buy screws', createdAt: T0, updatedAt: 0, order: 0, orderedAt: 0, ...over };
}
function doc(items, exportedAt, extra = {}) {
	return { version: 1, exportedAt, items, deletedIds: [], ...extra };
}

const cases = [
	{
		name: 'reopen stays open',
		a: doc([task({ updatedAt: T2, completedAt: null })], T2),
		b: doc([task({ completedAt: T1 })], T1),
	},
	{
		name: 'completion beats open',
		a: doc([task({})], T2),
		b: doc([task({ completedAt: T1 })], T1),
	},
	{
		name: 'deleted id drops the item',
		a: doc([task({ id: 'keep' }), task({ id: 'gone' })], 10, { deletedIds: ['gone'] }),
		b: doc([task({ id: 'gone', text: 'still here' })], 5),
	},
	{
		name: 'unpin drops the package',
		a: { exportedAt: 20, pins: ['keep'], pinChanges: [{ packageName: 'gone', pinned: false, at: 20 }] },
		b: { exportedAt: 10, pins: ['keep', 'gone'] },
	},
	{
		name: 'equal stamp prefers unpin',
		a: { exportedAt: 10, pins: ['app'], pinChanges: [{ packageName: 'app', pinned: true, at: 10 }] },
		b: { exportedAt: 10, pins: [], pinChanges: [{ packageName: 'app', pinned: false, at: 10 }] },
	},
	{
		name: 'later pin beats unpin',
		a: { exportedAt: 30, pins: ['app'], pinChanges: [{ packageName: 'app', pinned: true, at: 30 }] },
		b: { exportedAt: 10, pins: [], pinChanges: [{ packageName: 'app', pinned: false, at: 10 }] },
	},
	{
		name: 'deleted alarm is dropped',
		a: { exportedAt: 20, alarms: [{ id: 'a1', hour: 7, minute: 0, enabled: true }], deletedAlarmIds: ['a1'] },
		b: { exportedAt: 10, alarms: [{ id: 'a1', hour: 7, minute: 0, enabled: true }] },
	},
	{
		name: 'dismissed alarm keeps the later fire',
		a: { exportedAt: 20, alarms: [{ id: 'a1', hour: 7, minute: 0, enabled: true, lastFiredAt: 40, updatedAt: 10 }] },
		b: { exportedAt: 10, alarms: [{ id: 'a1', hour: 7, minute: 0, enabled: true, lastFiredAt: 5, updatedAt: 10 }] },
	},
	{
		name: 'equal stamp prefers a disabled alarm',
		a: { exportedAt: 10, alarms: [{ id: 'a1', hour: 7, minute: 30, enabled: false, updatedAt: 10, snoozeUntil: 99 }] },
		b: { exportedAt: 10, alarms: [{ id: 'a1', hour: 7, minute: 30, enabled: true, updatedAt: 10, snoozeUntil: 50 }] },
	},
	{
		name: 'newer enable turns the alarm back on',
		a: { exportedAt: 30, alarms: [{ id: 'a1', hour: 7, minute: 0, enabled: true, updatedAt: 30 }] },
		b: { exportedAt: 10, alarms: [{ id: 'a1', hour: 7, minute: 0, enabled: false, updatedAt: 10 }] },
	},
	{
		name: 'equal play stamp keeps a skip',
		a: { exportedAt: 50, podcasts: { progress: [{ episodeId: 'ep', lastPlayedAt: 40, skipped: true, savedAt: 1 }] } },
		b: { exportedAt: 40, podcasts: { progress: [{ episodeId: 'ep', lastPlayedAt: 40, skipped: false, savedAt: 5 }] } },
	},
	{
		name: 'blank credentials do not wipe the older key',
		a: { exportedAt: 20, settings: { apiKey: '', model: 'new', weatherPlace: 'Kitchener' } },
		b: { exportedAt: 10, settings: { apiKey: 'sk-old', model: 'old', oauthRefresh: 'r' } },
	},
	{
		name: 'order stamp is independent of text',
		a: doc([task({ text: 'old words', updatedAt: 5, order: 9, orderedAt: 50 })], 50),
		b: doc([task({ text: 'new words', updatedAt: 40, order: 1, orderedAt: 10 })], 40),
	},
	{
		name: 're-added article clears its delete on the newer side',
		a: {
			exportedAt: 30,
			reads: { articles: [{ id: 'r1', title: 'back', addedAt: 30, text: 'hello' }], deletedIds: [], progress: [] },
		},
		b: {
			exportedAt: 10,
			reads: { articles: [], deletedIds: ['r1'], progress: [{ articleId: 'r1', positionMs: 4, updatedAt: 8 }] },
		},
	},
	{
		name: 'deleted habit drops the habit and its marks',
		a: {
			exportedAt: 20,
			habits: [{ id: 'h1', name: 'stretch', kind: 'daily', updatedAt: 20, createdAt: 1, enabled: true }],
			habitMarks: [{ id: 'h1:1', habitId: 'h1', slotAt: 1, done: true, updatedAt: 2 }],
			deletedHabitIds: ['h1'],
		},
		b: {
			exportedAt: 10,
			habits: [{ id: 'h1', name: 'stretch', kind: 'daily', updatedAt: 10, createdAt: 1, enabled: true }],
			habitMarks: [{ id: 'h1:1', habitId: 'h1', slotAt: 1, done: false, updatedAt: 1 }],
		},
	},
	{
		name: 'newer habit check wins',
		a: {
			exportedAt: 20,
			habits: [{ id: 'h1', name: 'water', kind: 'interval', everyMinutes: 240, times: [480], updatedAt: 5, createdAt: 1, enabled: true }],
			habitMarks: [{ id: 'h1:8', habitId: 'h1', slotAt: 8, done: true, updatedAt: 30 }],
		},
		b: {
			exportedAt: 10,
			habits: [{ id: 'h1', name: 'water', kind: 'interval', everyMinutes: 240, times: [480], updatedAt: 5, createdAt: 1, enabled: true }],
			habitMarks: [{ id: 'h1:8', habitId: 'h1', slotAt: 8, done: false, updatedAt: 10 }],
		},
	},
	{
		name: 'slim drops episodes and unplayed progress',
		a: {
			exportedAt: 1,
			watchlist: [{ symbol: 'brk.b', name: 'Berkshire', price: 1, addedAt: 2 }],
			podcasts: {
				shows: [{ feedUrl: 'https://ex.ample/feed', title: 'Show' }],
				episodes: [{ id: 'ep', title: 'One' }],
				progress: [
					{ episodeId: 'played', lastPlayedAt: 3 },
					{ episodeId: 'saved', savedAt: 4 },
					{ episodeId: 'skip', skipped: true },
					{ episodeId: 'cold', lastPlayedAt: 0 },
				],
			},
		},
		b: emptyDoc(1),
	},
];

function pack(entry) {
	return {
		name: entry.name,
		a: entry.a,
		b: entry.b,
		ab: mergeDocs(entry.a, entry.b),
		ba: mergeDocs(entry.b, entry.a),
		slimA: slimDoc(entry.a),
	};
}

const next = JSON.stringify(cases.map(pack), null, 2) + '\n';
if (process.argv.includes('--write')) {
	writeFileSync(fixturePath, next);
	console.log(`wrote ${cases.length} cases`);
	process.exit(0);
}
const current = readFileSync(fixturePath, 'utf8');
if (current !== next) {
	console.error('merge fixtures are stale. Run: node bin/check-merge.mjs --write');
	process.exit(1);
}
console.log(`merge fixtures match (${cases.length})`);

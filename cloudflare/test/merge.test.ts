import assert from 'node:assert/strict';
import test from 'node:test';
import { mergeDocs } from '../public/merge.js';

const T0 = 1_760_000_000_000; // task created
const T1 = T0 + 86_400_000; // checked off, then synced everywhere
const T2 = T1 + 43_200_000; // checked back on, one day later

function task(over) {
	return {
		id: 'abc',
		kind: 'todo',
		text: 'buy screws',
		createdAt: T0,
		updatedAt: 0,
		order: 0,
		orderedAt: 0,
		...over,
	};
}

function doc(items, exportedAt) {
	return { version: 1, exportedAt, items, deletedIds: [] };
}

// A reopen clears completedAt, so the reopen must carry a fresh updatedAt or
// its stamp stays at createdAt and the older completed snapshot keeps winning
// the merge. Both merge orders are asserted: the client joins local-first
// (joinDocs), the Worker's putVault joins vault-snapshot-first.
test('a task reopened after sync stays reopened in either merge order', () => {
	const local = doc([task({ updatedAt: T2, completedAt: null })], T2);
	const remote = doc([task({ completedAt: T1 })], T1);
	for (const merged of [mergeDocs(local, remote), mergeDocs(remote, local)]) {
		assert.equal(merged.items.length, 1);
		assert.equal(merged.items[0].completedAt, null);
	}
});

// The fix must not break the normal direction: a completion made on one
// device still propagates over a device that never saw it.
test('a fresh completion beats an unsynced open copy in either merge order', () => {
	const open = doc([task({})], T2);
	const done = doc([task({ completedAt: T1 })], T1);
	for (const merged of [mergeDocs(open, done), mergeDocs(done, open)]) {
		assert.equal(merged.items.length, 1);
		assert.equal(merged.items[0].completedAt, T1);
	}
});

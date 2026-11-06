import assert from 'node:assert/strict';
import test from 'node:test';
import { ORDER_QUIET_MS, PLAY_SAVE_MS, TEXT_QUIET_MS, WRITE_QUIET_MS, autoSyncDelay, settlePushedDoc, syncReadyAt } from '../public/web/sync-quiet.js';

test('typing and reordering hold an automatic push', () => {
	assert.equal(TEXT_QUIET_MS, 8_000);
	assert.equal(ORDER_QUIET_MS, 5_000);
	assert.equal(WRITE_QUIET_MS, 2_000);
	assert.equal(PLAY_SAVE_MS, 10_000);
	const now = 10_000;
	assert.equal(syncReadyAt(now, [{ kind: 'text', at: 9_000 }]), 17_000);
	assert.equal(syncReadyAt(now, [{ kind: 'order', at: 9_000 }]), 14_000);
	assert.equal(syncReadyAt(now, [{ kind: 'write', at: 9_000 }]), 11_000);
	assert.equal(
		syncReadyAt(now, [
			{ kind: 'write', at: 9_000 },
			{ kind: 'order', at: 9_500 },
		]),
		14_500,
	);
	assert.equal(syncReadyAt(20_000, [{ kind: 'text', at: 9_000 }]), 20_000);
});

test('a push keeps keystrokes and order changes made during the request', () => {
	const join = (local, remote) => ({ ...remote, ...local, from: 'join' });
	const keepFresh = (merged) => merged;
	const snapshot = { text: 'hel', order: 1 };
	const live = { text: 'hello', order: 4 };
	const remote = { text: 'hel', order: 1, cloud: true };
	const settled = settlePushedDoc(snapshot, live, remote, true, join, keepFresh, 1);
	assert.equal(settled.dirty, true);
	assert.equal(settled.doc.text, 'hello');
	assert.equal(settled.doc.order, 4);
	const quiet = settlePushedDoc(snapshot, live, remote, false, join, keepFresh, 1);
	assert.equal(quiet.dirty, false);
	assert.equal(quiet.doc.text, 'hel');
});

test('an open keyboard defers the automatic sync', () => {
	assert.equal(autoSyncDelay(0, false, false), 0);
	assert.equal(autoSyncDelay(4_000, false, true), 4_000);
	assert.equal(autoSyncDelay(0, true, false), 1_000);
	assert.equal(autoSyncDelay(0, false, true), 1_000);
});

import assert from 'node:assert/strict';
import test from 'node:test';
import { dayLabel, dayStart, graph, openSlot, pageAfterSwipe, parseHabit, slotsOnDay, toggleMark, dueReminder, habitRemindersOn, habitSoundName } from '../public/web/habits.js';

const zone = 'America/Toronto';
const morning = Date.parse('2026-11-15T06:00:00-05:00');

test('parses daily, every 4 hours, and 7am and 7pm', () => {
	assert.equal(parseHabit('stretch daily').kind, 'daily');
	assert.equal(parseHabit('stretch daily').times[0], 8 * 60);
	assert.equal(parseHabit('vitamins daily at 9am').times[0], 9 * 60);
	assert.equal(parseHabit('water every 4 hours').everyMinutes, 240);
	assert.deepEqual(parseHabit('meds 7am and 7pm daily').times, [7 * 60, 19 * 60]);
	assert.equal(parseHabit('every 5 hours starting at 8am'), null);
	assert.equal(parseHabit('daily'), null);
});

test('graph is completed over due', () => {
	const day = dayStart(morning, zone);
	const stretch = { id: 'h', name: 'stretch', kind: 'daily', times: [480], createdAt: day - 86_400_000, enabled: true };
	const meds = { id: 'meds', name: 'meds', kind: 'times', times: [420, 1140], createdAt: day - 86_400_000, enabled: true };
	assert.equal(slotsOnDay(meds, day, zone).length, 2);
	const slot = openSlot(meds, day + 6 * 3_600_000, zone);
	assert.equal(slot, day + 7 * 3_600_000);
	const marks = toggleMark([], stretch.id, day, morning);
	const stat = graph([stretch, meds], marks, [day], zone)[0];
	assert.equal(stat.completed, 1);
	assert.equal(stat.due, 3);
	assert.equal(dayLabel(stat), '1 of 3 · 33%');
	assert.equal(dueReminder([meds], toggleMark([], meds.id, slot, morning), slot, new Set(), zone), null);
});

test('swipe on tasks reveals habits', () => {
	assert.equal(pageAfterSwipe(0, -80, 48), 1);
	assert.equal(pageAfterSwipe(1, 80, 48), 0);
});

test('reminders default on and the tone matches the clock list', () => {
	assert.equal(habitRemindersOn({}), true);
	assert.equal(habitRemindersOn({ habitReminders: false }), false);
	assert.equal(habitSoundName({}), 'PULSE');
	assert.equal(habitSoundName({ habitSound: 'bell' }), 'BELL');
	assert.equal(habitSoundName({ habitSound: 'nope' }), 'PULSE');
});

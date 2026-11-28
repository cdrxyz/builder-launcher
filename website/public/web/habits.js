export const DEFAULT_MINUTE = 8 * 60;
export const DAY_MINUTES = 24 * 60;
export const PREVIEW = 3;
export const GRAPH_DAYS = 14;
export const HINT = 'stretch daily · daily at noon Vitamin D · water every 4 hours';
export const HABIT_SOUNDS = ['PULSE', 'CHIME', 'BELL', 'ORTHODOX', 'HUM', 'OFF'];

export function habitRemindersOn(settings) {
	return settings?.habitReminders !== false;
}

export function habitSoundName(settings) {
	const name = String(settings?.habitSound || 'PULSE').toUpperCase();
	return HABIT_SOUNDS.includes(name) ? name : 'PULSE';
}

const INTERVAL = /\bevery\s+(\d+)\s*(hours?|hrs?|h|minutes?|mins?|m)\b/i;
const NAMED = /(?<![A-Za-z0-9])(noon|midday|midnight)\b/gi;
const AMPM = /(?<![A-Za-z0-9])(\d{1,2})(?::([0-5]\d))?\s*(am|pm|a|p)\b/gi;
const COLON = /(?<![A-Za-z0-9])([01]?\d|2[0-3]):([0-5]\d)\b/g;
const COMPACT = /(?<![A-Za-z0-9])(\d{3,4})\b/g;
const DAILY_WORD = /\b(?:daily|every\s+day|everyday)\b/i;
const FILLER = /\b(?:starting at|starts at|from|at|and|daily|every\s+day|everyday)\b|[,]+/gi;

export function parseHabit(raw) {
	const text = String(raw || '').trim().replace(/\s+/g, ' ');
	if (!text) return null;
	const interval = text.match(INTERVAL);
	if (interval) {
		const count = Number(interval[1]);
		const minutes = interval[2].toLowerCase().startsWith('h') ? count * 60 : count;
		if (!minutes || minutes > DAY_MINUTES || DAY_MINUTES % minutes !== 0) return null;
		const after = text.slice(interval.index + interval[0].length);
		const start = clockHits(after)[0]?.minute ?? DEFAULT_MINUTE;
		const name = cleanName(text.slice(0, interval.index) + text.slice(interval.index + interval[0].length));
		if (!name) return null;
		return { name, kind: 'interval', everyMinutes: minutes, times: [start] };
	}
	const times = clockHits(text).map((hit) => hit.minute);
	if (times.length >= 2) {
		const name = cleanName(text);
		if (!name) return null;
		return { name, kind: 'times', everyMinutes: 0, times: [...new Set(times)].sort((a, b) => a - b) };
	}
	if (DAILY_WORD.test(text) || times.length === 1) {
		const name = cleanName(text);
		if (!name) return null;
		return { name, kind: 'daily', everyMinutes: 0, times: [times[0] ?? DEFAULT_MINUTE] };
	}
	return null;
}

export function phrase(draft) {
	if (!draft) return '';
	if (draft.kind === 'daily') {
		const minute = (draft.times && draft.times[0]) || DEFAULT_MINUTE;
		return minute === DEFAULT_MINUTE ? `${draft.name} daily` : `${draft.name} daily at ${formatMinute(minute)}`;
	}
	if (draft.kind === 'interval') {
		const every = draft.everyMinutes % 60 === 0 ? `every ${draft.everyMinutes / 60} hours` : `every ${draft.everyMinutes} minutes`;
		const start = (draft.times && draft.times[0]) || DEFAULT_MINUTE;
		return start === DEFAULT_MINUTE ? `${draft.name} ${every}` : `${draft.name} ${every} starting at ${formatMinute(start)}`;
	}
	return `${draft.name} ${(draft.times || []).map(formatMinute).join(' and ')}`;
}

export function applyEdit(habit, draft, now) {
	return {
		...habit,
		name: draft.name,
		kind: draft.kind,
		everyMinutes: draft.everyMinutes,
		times: draft.times,
		updatedAt: now,
	};
}

export function zoneOf(zone) {
	return zone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

export function dayStart(epoch, zone) {
	const parts = partsOf(epoch, zone);
	return Date.UTC(parts.year, parts.month - 1, parts.day) - offsetMs(parts.year, parts.month, parts.day, zone);
}

export function plusDays(start, days, zone) {
	const parts = partsOf(start + 12 * 3600_000, zone);
	const next = new Date(Date.UTC(parts.year, parts.month - 1, parts.day + days));
	return Date.UTC(next.getUTCFullYear(), next.getUTCMonth(), next.getUTCDate()) - offsetMs(next.getUTCFullYear(), next.getUTCMonth() + 1, next.getUTCDate(), zone);
}

export function slotsOnDay(habit, start, zone) {
	if (!habit || habit.enabled === false) return [];
	const end = plusDays(start, 1, zone);
	if ((habit.createdAt || 0) >= end) return [];
	if (habit.kind === 'daily') return [start];
	const minutes = habit.kind === 'interval' ? intervalMinutes(habit) : habit.times || [];
	return minutes.map((minute) => start + minute * 60_000).filter((slot) => slot >= (habit.createdAt || 0) && slot < end);
}

export function openSlot(habit, now, zone) {
	const slots = slotsOnDay(habit, dayStart(now, zone), zone);
	if (!slots.length) return null;
	if (habit.kind === 'daily') return slots[0];
	const current = [...slots].reverse().find((slot) => slot <= now);
	return current ?? slots.find((slot) => slot > now) ?? null;
}

export function markId(habitId, slotAt) {
	return `${habitId}:${slotAt}`;
}

export function isDone(marks, habitId, slotAt) {
	return (marks || []).some((mark) => mark.habitId === habitId && mark.slotAt === slotAt && mark.done);
}

export function toggleMark(marks, habitId, slotAt, now) {
	const id = markId(habitId, slotAt);
	const prev = (marks || []).find((mark) => mark.id === id);
	const next = { id, habitId, slotAt, done: !(prev && prev.done), updatedAt: now };
	return [...(marks || []).filter((mark) => mark.id !== id), next];
}

export function graph(habits, marks, starts, zone) {
	return (starts || []).map((start) => {
		let due = 0;
		let completed = 0;
		for (const habit of habits || []) {
			for (const slot of slotsOnDay(habit, start, zone)) {
				due += 1;
				if (isDone(marks, habit.id, slot)) completed += 1;
			}
		}
		return { startMs: start, completed, due, percent: due ? Math.floor((completed * 100) / due) : 0 };
	});
}

export function recentDayStarts(now, days = GRAPH_DAYS, zone) {
	const today = dayStart(now, zone);
	const starts = [];
	for (let i = days - 1; i >= 0; i -= 1) starts.push(plusDays(today, -i, zone));
	return starts;
}

export function dayLabel(stat) {
	return `${stat.completed} of ${stat.due} · ${stat.percent}%`;
}

export function pageAfterSwipe(page, dx, threshold) {
	if (dx <= -threshold) return 1;
	if (dx >= threshold) return 0;
	return page;
}

export function reminderAt(habit, slotAt, zone) {
	if (habit.kind === 'daily') return dayStart(slotAt, zone) + ((habit.times && habit.times[0]) || DEFAULT_MINUTE) * 60_000;
	return slotAt;
}

export function dueReminder(habits, marks, now, reminded, zone) {
	for (const habit of habits || []) {
		if (habit.enabled === false) continue;
		const slot = openSlot(habit, now, zone);
		if (slot == null || isDone(marks, habit.id, slot)) continue;
		if (now < reminderAt(habit, slot, zone)) continue;
		const key = markId(habit.id, slot);
		if (reminded && reminded.has(key)) continue;
		return { habitId: habit.id, slotAt: slot, name: habit.name };
	}
	return null;
}

export function nextReminderAt(habits, marks, now, zone) {
	let next = null;
	const today = dayStart(now, zone);
	for (const habit of habits || []) {
		if (habit.enabled === false) continue;
		for (const day of [today, plusDays(today, 1, zone)]) {
			for (const slot of slotsOnDay(habit, day, zone)) {
				if (isDone(marks, habit.id, slot)) continue;
				const at = reminderAt(habit, slot, zone);
				if (at <= now) {
					if (openSlot(habit, now, zone) === slot) next = now;
					continue;
				}
				if (next == null || at < next) next = at;
			}
		}
	}
	return next;
}

export function scheduleLabel(habit) {
	if (habit.kind === 'daily') {
		const minute = (habit.times && habit.times[0]) || DEFAULT_MINUTE;
		return minute === DEFAULT_MINUTE ? 'daily' : `daily at ${formatMinute(minute)}`;
	}
	if (habit.kind === 'interval') {
		return habit.everyMinutes % 60 === 0 ? `every ${habit.everyMinutes / 60} hours` : `every ${habit.everyMinutes} minutes`;
	}
	return (habit.times || []).map(formatMinute).join(' and ');
}

export function formatMinute(minute) {
	const h24 = Math.floor(minute / 60);
	const m = minute % 60;
	const ap = h24 < 12 ? 'am' : 'pm';
	const h = h24 % 12 === 0 ? 12 : h24 % 12;
	return m === 0 ? `${h}${ap}` : `${h}:${String(m).padStart(2, '0')}${ap}`;
}

export function mergeHabit(a, b) {
	const au = Math.max(a.updatedAt || 0, a.createdAt || 0);
	const bu = Math.max(b.updatedAt || 0, b.createdAt || 0);
	if (au !== bu) return au > bu ? a : b;
	if (a.enabled === false && b.enabled !== false) return a;
	if (b.enabled === false && a.enabled !== false) return b;
	return b;
}

export function mergeHabits(a, b) {
	const map = new Map();
	for (const item of [...(a || []), ...(b || [])]) {
		const prev = map.get(String(item.id));
		map.set(String(item.id), prev ? mergeHabit(prev, item) : item);
	}
	return [...map.values()];
}

export function mergeMarks(a, b) {
	const map = new Map();
	for (const item of [...(a || []), ...(b || [])]) {
		const prev = map.get(String(item.id));
		map.set(String(item.id), !prev || (item.updatedAt || 0) >= (prev.updatedAt || 0) ? item : prev);
	}
	return [...map.values()];
}

function intervalMinutes(habit) {
	const step = habit.everyMinutes || 0;
	if (step <= 0) return [];
	const phase = ((habit.times && habit.times[0]) || DEFAULT_MINUTE) % step;
	const minutes = [];
	for (let minute = phase; minute < DAY_MINUTES; minute += step) minutes.push(minute);
	return minutes;
}

function clockHits(text) {
	const found = [];
	const source = String(text);
	for (const match of source.matchAll(NAMED)) {
		found.push({ minute: match[1].toLowerCase() === 'midnight' ? 0 : 12 * 60, start: match.index, end: match.index + match[0].length });
	}
	for (const match of source.matchAll(AMPM)) {
		const minute = ampmMinute(match);
		if (minute != null) found.push({ minute, start: match.index, end: match.index + match[0].length });
	}
	for (const match of source.matchAll(COLON)) {
		const hour = Number(match[1]);
		const minute = Number(match[2]);
		if (hour <= 23 && minute <= 59) found.push({ minute: hour * 60 + minute, start: match.index, end: match.index + match[0].length });
	}
	for (const match of source.matchAll(COMPACT)) {
		const minute = compactMinute(match[1]);
		if (minute != null) found.push({ minute, start: match.index, end: match.index + match[0].length });
	}
	found.sort((a, b) => a.start - b.start || b.end - a.end);
	const kept = [];
	for (const hit of found) {
		if (!kept.some((prev) => prev.start <= hit.end - 1 && hit.start <= prev.end - 1)) kept.push(hit);
	}
	return kept;
}

function ampmMinute(match) {
	let hour = Number(match[1]);
	const minute = match[2] ? Number(match[2]) : 0;
	if (hour < 1 || hour > 12 || minute > 59) return null;
	if (hour === 12) hour = 0;
	if (String(match[3]).toLowerCase().startsWith('p')) hour += 12;
	return hour * 60 + minute;
}

function compactMinute(raw) {
	const hour = raw.length === 3 ? Number(raw.slice(0, 1)) : Number(raw.slice(0, 2));
	const minute = Number(raw.slice(-2));
	if (hour > 23 || minute > 59) return null;
	return hour * 60 + minute;
}

function cleanName(raw) {
	let text = String(raw);
	for (const hit of [...clockHits(text)].reverse()) text = text.slice(0, hit.start) + text.slice(hit.end);
	return text.replace(FILLER, ' ').replace(/\s+/g, ' ').trim();
}

function partsOf(epoch, zone) {
	const fmt = new Intl.DateTimeFormat('en-US', {
		timeZone: zoneOf(zone),
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
		hour: '2-digit',
		minute: '2-digit',
		second: '2-digit',
		hourCycle: 'h23',
	});
	const bag = Object.fromEntries(fmt.formatToParts(new Date(epoch)).map((part) => [part.type, part.value]));
	return { year: Number(bag.year), month: Number(bag.month), day: Number(bag.day) };
}

function offsetMs(year, month, day, zone) {
	const utc = Date.UTC(year, month - 1, day);
	const fmt = new Intl.DateTimeFormat('en-US', {
		timeZone: zoneOf(zone),
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
		hour: '2-digit',
		minute: '2-digit',
		second: '2-digit',
		hourCycle: 'h23',
	});
	const bag = Object.fromEntries(fmt.formatToParts(new Date(utc)).map((part) => [part.type, part.value]));
	const asUtc = Date.UTC(Number(bag.year), Number(bag.month) - 1, Number(bag.day), Number(bag.hour), Number(bag.minute), Number(bag.second));
	return asUtc - utc;
}

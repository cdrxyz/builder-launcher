import { alignOpenOrder } from './items.js';
import { mergeReads } from './reads.js';

function restoredArticleIds(doc) {
	const bag = doc?.reads || {};
	const deleted = new Set((bag.deletedIds || []).map(String));
	return (bag.articles || [])
		.map((item) => String(item?.id || ''))
		.filter((id) => id && !deleted.has(id));
}

export function keepFreshArticles(merged, local, since) {
	const bag = local?.reads || {};
	const deleted = new Set((bag.deletedIds || []).map(String));
	const fresh = (bag.articles || []).filter((item) => {
		const id = String(item?.id || '');
		return id && !deleted.has(id) && (item.addedAt || 0) >= since;
	});
	if (!fresh.length) return merged || local;
	return {
		...(merged || local),
		reads: mergeReads(
			{ articles: fresh, progress: bag.progress || [], deletedIds: [] },
			merged?.reads,
			{ restoredIds: fresh.map((item) => String(item.id)) },
		),
	};
}

function stamp(item) {
	if (!item || typeof item !== 'object') return 0;
	return Math.max(item.updatedAt || 0, item.completedAt || 0, item.createdAt || 0, item.lastPlayedAt || 0);
}

function byId(list) {
	const map = new Map();
	for (const item of list || []) {
		if (!item || item.id == null) continue;
		map.set(String(item.id), item);
	}
	return map;
}

function pickOrder(x, y, content) {
	const ax = x.orderedAt || 0;
	const ay = y.orderedAt || 0;
	if (ax !== ay) return ax > ay ? x : y;
	const ox = x.order || 0;
	const oy = y.order || 0;
	if (ox !== 0 && oy === 0) return x;
	if (oy !== 0 && ox === 0) return y;
	return content;
}

function mergeItem(x, y) {
	if (!x) return y;
	if (!y) return x;
	const content = stamp(x) >= stamp(y) ? x : y;
	const orderSource = pickOrder(x, y, content);
	if (orderSource === content) return content;
	return { ...content, order: orderSource.order || 0, orderedAt: orderSource.orderedAt || 0 };
}

function mergeItemList(a, b) {
	const left = byId(a);
	const right = byId(b);
	const ids = new Set([...left.keys(), ...right.keys()]);
	const out = [];
	for (const id of ids) out.push(mergeItem(left.get(id), right.get(id)));
	return out;
}

function mergeAlarm(x, y) {
	const last = Math.max(x.lastFiredAt || 0, y.lastFiredAt || 0);
	const sx = x.updatedAt || 0;
	const sy = y.updatedAt || 0;
	let base = x;
	if (sy > sx) base = y;
	else if (sx > sy) base = x;
	else if (x.enabled === false && y.enabled !== false) base = x;
	else if (y.enabled === false && x.enabled !== false) base = y;
	const snooze = base.enabled === false
		? 0
		: sx !== sy
			? (base.snoozeUntil || 0)
			: Math.max(x.snoozeUntil || 0, y.snoozeUntil || 0);
	return {
		...base,
		lastFiredAt: last || null,
		snoozeUntil: snooze > last ? snooze : null,
	};
}

function mergeAlarms(a, b) {
	const left = byId(a);
	const right = byId(b);
	const ids = new Set([...left.keys(), ...right.keys()]);
	const out = [];
	for (const id of ids) {
		const x = left.get(id);
		const y = right.get(id);
		if (!x) out.push(y);
		else if (!y) out.push(x);
		else out.push(mergeAlarm(x, y));
	}
	return out;
}

function mergeById(a, b) {
	const left = byId(a);
	const right = byId(b);
	const ids = new Set([...left.keys(), ...right.keys()]);
	const out = [];
	for (const id of ids) {
		const x = left.get(id);
		const y = right.get(id);
		if (!x) out.push(y);
		else if (!y) out.push(x);
		else out.push(stamp(x) >= stamp(y) ? x : y);
	}
	return out;
}

function mergeWatch(a, b, preferA) {
	const seen = new Set();
	const out = [];
	const first = preferA ? a || [] : b || [];
	const second = preferA ? b || [] : a || [];
	for (const item of [...first, ...second]) {
		const symbol = String(item?.symbol || '').toUpperCase();
		if (!symbol || seen.has(symbol)) continue;
		seen.add(symbol);
		out.push(item);
	}
	return out;
}

function mergeShows(a, b) {
	const map = new Map();
	for (const show of [...(a || []), ...(b || [])]) {
		const key = String(show?.feedUrl || '').toLowerCase();
		if (!key) continue;
		const prev = map.get(key);
		if (!prev || (show.subscribedAt || 0) >= (prev.subscribedAt || 0)) map.set(key, show);
	}
	return [...map.values()];
}

function mergeProgress(a, b) {
	const map = new Map();
	for (const row of [...(a || []), ...(b || [])]) {
		const id = String(row?.episodeId || '');
		if (!id) continue;
		const prev = map.get(id);
		if (!prev) {
			map.set(id, row);
			continue;
		}
		const nextAt = row.lastPlayedAt || 0;
		const prevAt = prev.lastPlayedAt || 0;
		let chosen = prev;
		if (nextAt > prevAt) chosen = row;
		else if (nextAt < prevAt) chosen = prev;
		else if (Boolean(row.skipped) !== Boolean(prev.skipped)) chosen = row.skipped ? row : prev;
		else chosen = row;
		const savedAt = Math.max(chosen.savedAt || 0, prev.savedAt || 0, row.savedAt || 0);
		map.set(id, savedAt === (chosen.savedAt || 0) ? chosen : { ...chosen, savedAt });
	}
	return [...map.values()];
}

function mergeEpisode(x, y) {
	if (!x) return y;
	if (!y) return x;
	const newer = (x.pubDate || 0) >= (y.pubDate || 0) ? x : y;
	const older = newer === x ? y : x;
	return {
		...older,
		...newer,
		description: newer.description || older.description || '',
		title: newer.title || older.title || '',
		enclosureUrl: newer.enclosureUrl || older.enclosureUrl || '',
		durationMs: Math.max(newer.durationMs || 0, older.durationMs || 0),
	};
}

function mergeEpisodes(a, b) {
	const left = byId(a);
	const right = byId(b);
	const ids = new Set([...left.keys(), ...right.keys()]);
	const out = [];
	for (const id of ids) out.push(mergeEpisode(left.get(id), right.get(id)));
	return out;
}

function mergePods(a, b) {
	const left = a || {};
	const right = b || {};
	return {
		shows: mergeShows(left.shows, right.shows),
		episodes: mergeEpisodes(left.episodes, right.episodes),
		progress: mergeProgress(left.progress, right.progress),
		cacheBytes: Math.max(left.cacheBytes || 0, right.cacheBytes || 0),
	};
}

function slimWatch(list) {
	return (list || []).map((item) => ({
		symbol: item.symbol,
		name: item.name || item.symbol,
		addedAt: item.addedAt || 0,
		exchange: item.exchange || '',
	}));
}

export function slimDoc(doc) {
	if (!doc || typeof doc !== 'object') return doc;
	const pods = doc.podcasts || {};
	const reads = doc.reads || {};
	return {
		...doc,
		watchlist: slimWatch(doc.watchlist),
		podcasts: {
			shows: pods.shows || [],
			episodes: [],
			progress: (pods.progress || []).filter((row) => (row?.lastPlayedAt || 0) > 0 || (row?.savedAt || 0) > 0 || row?.skipped === true),
		},
		reads: {
			articles: reads.articles || [],
			progress: reads.progress || [],
			deletedIds: reads.deletedIds || [],
		},
	};
}

export function isQuotaError(err) {
	if (!err) return false;
	if (err.name === 'QuotaExceededError' || err.code === 22 || err.code === 1014) return true;
	return /quota has been exceeded/i.test(String(err.message || err));
}

export function writeSnapshot(storage, key, doc) {
	const payload = JSON.stringify(slimDoc(doc));
	try {
		storage.setItem(key, payload);
		return true;
	} catch (err) {
		if (!isQuotaError(err)) throw err;
		try {
			storage.removeItem(key);
			storage.setItem(key, payload);
			return true;
		} catch {
			return false;
		}
	}
}

function unique(list) {
	const seen = new Set();
	const out = [];
	for (const item of list || []) {
		const key = String(item);
		if (seen.has(key)) continue;
		seen.add(key);
		out.push(item);
	}
	return out;
}

function pickPinChange(prev, change) {
	if (!prev) return change;
	const at = change.at || 0;
	const prevAt = prev.at || 0;
	if (at > prevAt) return change;
	if (at < prevAt) return prev;
	if (change.pinned === false) return change;
	if (prev.pinned === false) return prev;
	return change;
}

function mergePins(aPins, bPins, aChanges, bChanges) {
	const latest = new Map();
	for (const change of [...(aChanges || []), ...(bChanges || [])]) {
		if (!change || change.packageName == null || change.packageName === '') continue;
		const key = String(change.packageName);
		latest.set(key, pickPinChange(latest.get(key), change));
	}
	const pins = [];
	const seen = new Set();
	for (const pkg of [...(aPins || []), ...(bPins || [])]) {
		const key = String(pkg);
		const change = latest.get(key);
		if (change && change.pinned === false) continue;
		if (seen.has(key)) continue;
		seen.add(key);
		pins.push(pkg);
	}
	for (const [key, change] of latest) {
		if (change.pinned && !seen.has(key)) {
			seen.add(key);
			pins.push(change.packageName);
		}
	}
	return { pins, pinChanges: [...latest.values()] };
}

export function joinDocs(local, remote) {
	if (!remote) return local || emptyDoc();
	if (!local) return remote;
	return mergeDocs(local, remote);
}

export function mergeDocs(a, b) {
	if (!a) return b || null;
	if (!b) return a;
	const preferA = (a.exportedAt || 0) >= (b.exportedAt || 0);
	const newer = preferA ? a : b;
	const older = preferA ? b : a;
	const deletedIds = unique([...(a.deletedIds || []), ...(b.deletedIds || [])]);
	const deleted = new Set(deletedIds.map(String));
	const mergedItems = mergeItemList(a.items, b.items).filter((item) => !deleted.has(String(item.id)));
	const items = alignOpenOrder(a.items, b.items, mergedItems);
	const deletedAlarmIds = unique([...(a.deletedAlarmIds || []), ...(b.deletedAlarmIds || [])]);
	const dropAlarms = new Set(deletedAlarmIds.map(String));
	const pinMerge = mergePins(a.pins, b.pins, a.pinChanges, b.pinChanges);
	return {
		...older,
		...newer,
		version: Math.max(a.version || 1, b.version || 1),
		exportedAt: Math.max(a.exportedAt || 0, b.exportedAt || 0),
		items,
		deletedIds,
		chats: mergeById(a.chats, b.chats),
		pins: pinMerge.pins,
		pinChanges: pinMerge.pinChanges,
		watchlist: mergeWatch(a.watchlist, b.watchlist, preferA),
		podcasts: mergePods(a.podcasts, b.podcasts),
		reads: mergeReads(a.reads, b.reads, { restoredIds: restoredArticleIds(newer) }),
		alarms: mergeAlarms(a.alarms, b.alarms).filter((alarm) => !dropAlarms.has(String(alarm.id))),
		zones: mergeById(a.zones, b.zones),
		settings: newer.settings || older.settings,
		deletedAlarmIds,
	};
}

export function emptyDoc(now = Date.now()) {
	return {
		version: 1,
		exportedAt: now,
		items: [],
		deletedIds: [],
		watchlist: [],
		podcasts: { shows: [], episodes: [], progress: [] },
		reads: { articles: [], progress: [], deletedIds: [] },
	};
}

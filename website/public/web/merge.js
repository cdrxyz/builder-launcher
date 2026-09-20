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
		if (!prev || (row.lastPlayedAt || 0) >= (prev.lastPlayedAt || 0)) map.set(id, row);
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
	return {
		...doc,
		watchlist: slimWatch(doc.watchlist),
		podcasts: {
			shows: pods.shows || [],
			episodes: [],
			progress: (pods.progress || []).filter((row) => (row?.lastPlayedAt || 0) > 0),
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
	const items = mergeById(a.items, b.items).filter((item) => !deleted.has(String(item.id)));
	return {
		...older,
		...newer,
		version: Math.max(a.version || 1, b.version || 1),
		exportedAt: Math.max(a.exportedAt || 0, b.exportedAt || 0),
		items,
		deletedIds,
		chats: mergeById(a.chats, b.chats),
		pins: unique([...(a.pins || []), ...(b.pins || [])]),
		watchlist: mergeWatch(a.watchlist, b.watchlist, preferA),
		podcasts: mergePods(a.podcasts, b.podcasts),
		alarms: mergeById(a.alarms, b.alarms),
		zones: mergeById(a.zones, b.zones),
		settings: newer.settings || older.settings,
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
	};
}

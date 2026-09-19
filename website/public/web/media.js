export const STOCKS_MAX = 100;
export const PODCAST_CONTINUE = 3;
export const PODCAST_NEW = 5;
export const FINISH_REMAINING_MS = 30_000;

export function watchlist(doc) {
	return Array.isArray(doc?.watchlist) ? doc.watchlist : [];
}

export function looksLikeSymbol(query) {
	const q = String(query || '')
		.trim()
		.toUpperCase();
	if (q.length < 1 || q.length > 8) return false;
	return [...q].every((ch) => /[A-Z0-9.\-^]/.test(ch));
}

export function formatPrice(price, currency = 'USD') {
	if (price == null || Number.isNaN(price)) return '';
	const amount =
		price >= 1000
			? price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
			: price.toFixed(2);
	if (!currency || currency.toUpperCase() === 'USD') return `$${amount}`;
	return `${amount} ${currency}`;
}

export function formatPercent(percent) {
	if (percent == null || Number.isNaN(percent)) return '';
	const sign = percent >= 0 ? '+' : '';
	return `${sign}${percent.toFixed(2)}%`;
}

export function addTicker(list, item) {
	const symbol = String(item.symbol || '')
		.trim()
		.toUpperCase();
	if (!symbol) return list;
	if ((list || []).some((row) => String(row.symbol).toUpperCase() === symbol)) return list;
	if ((list || []).length >= STOCKS_MAX) return list;
	return [{ ...item, symbol, addedAt: item.addedAt || Date.now() }, ...(list || [])];
}

export function removeTicker(list, symbol) {
	const key = String(symbol || '').toUpperCase();
	return (list || []).filter((row) => String(row.symbol).toUpperCase() !== key);
}

export function parseYahooSearch(raw, limit = 8) {
	let root;
	try {
		root = JSON.parse(raw);
	} catch {
		return [];
	}
	const quotes = root?.quotes;
	if (!Array.isArray(quotes)) return [];
	const hits = [];
	for (const obj of quotes) {
		const symbol = String(obj?.symbol || '')
			.toUpperCase()
			.trim();
		if (!symbol) continue;
		hits.push({
			symbol,
			name: obj.shortname || obj.longname || obj.shortName || symbol,
			type: obj.typeDisp || obj.quoteType || '',
			exchange: obj.exchDisp || obj.exchange || '',
		});
		if (hits.length >= limit) break;
	}
	const seen = new Set();
	return hits.filter((hit) => (seen.has(hit.symbol) ? false : (seen.add(hit.symbol), true)));
}

export const STOCK_RANGES = [
	{ label: '1D', range: '1d', interval: '5m' },
	{ label: '1W', range: '5d', interval: '15m' },
	{ label: '1M', range: '1mo', interval: '1d' },
	{ label: '3M', range: '3mo', interval: '1d' },
	{ label: '1Y', range: '1y', interval: '1d' },
	{ label: '5Y', range: '5y', interval: '1wk' },
];

export const SPEED_STEPS = [0.8, 1, 1.1, 1.2, 1.4, 1.6, 1.8, 2, 2.5, 3];

export function parseYahooChart(raw) {
	let root;
	try {
		root = JSON.parse(raw);
	} catch {
		return null;
	}
	const result = root?.chart?.result?.[0];
	const meta = result?.meta;
	const symbol = String(meta?.symbol || '')
		.toUpperCase()
		.trim();
	const price = num(meta?.regularMarketPrice);
	if (!symbol || price == null) return null;
	const previous = num(meta?.chartPreviousClose) ?? num(meta?.previousClose) ?? price;
	const change = price - previous;
	const changePercent = previous === 0 ? 0 : (change / previous) * 100;
	const quoteArr = result?.indicators?.quote?.[0] || {};
	const timestamps = Array.isArray(result?.timestamp) ? result.timestamp : [];
	const closes = Array.isArray(quoteArr.close) ? quoteArr.close : [];
	const volumes = Array.isArray(quoteArr.volume) ? quoteArr.volume : [];
	const points = [];
	for (let i = 0; i < timestamps.length; i++) {
		const close = num(closes[i]);
		if (close == null) continue;
		points.push({ time: Number(timestamps[i]), close });
	}
	const highs = (quoteArr.high || []).map(num).filter((n) => n != null);
	const lows = (quoteArr.low || []).map(num).filter((n) => n != null);
	const volNums = volumes.map((v) => (v == null ? null : Number(v))).filter((n) => Number.isFinite(n));
	return {
		symbol,
		name: meta.shortName || meta.longName || symbol,
		price,
		previousClose: previous,
		change,
		changePercent,
		currency: meta.currency || 'USD',
		exchange: meta.exchangeName || meta.fullExchangeName || '',
		open: num(meta.regularMarketOpen) ?? num((quoteArr.open || []).find((n) => n != null)),
		high: num(meta.regularMarketDayHigh) ?? (highs.length ? Math.max(...highs) : null),
		low: num(meta.regularMarketDayLow) ?? (lows.length ? Math.min(...lows) : null),
		volume: num(meta.regularMarketVolume) ?? (volNums.length ? volNums[volNums.length - 1] : null),
		week52High: num(meta.fiftyTwoWeekHigh),
		week52Low: num(meta.fiftyTwoWeekLow),
		points,
		volumes: volNums,
	};
}

export function formatVolume(volume) {
	if (volume == null || Number.isNaN(volume)) return '—';
	const abs = Math.abs(volume);
	if (abs >= 1_000_000_000) return `${(volume / 1_000_000_000).toFixed(1)}B`;
	if (abs >= 1_000_000) return `${(volume / 1_000_000).toFixed(1)}M`;
	if (abs >= 1_000) return `${(volume / 1_000).toFixed(1)}K`;
	return String(volume);
}

export function formatNumber(value) {
	if (value == null || Number.isNaN(value)) return '—';
	if (Math.abs(value) >= 1000) {
		return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
	}
	return value.toFixed(2);
}

export function formatChange(change) {
	if (change == null || Number.isNaN(change)) return '';
	const sign = change >= 0 ? '+' : '';
	return `${sign}${change.toFixed(2)}`;
}

export function indexAt(x, width, count) {
	if (count <= 1 || width <= 0) return 0;
	const t = Math.min(1, Math.max(0, x / width));
	return Math.round(t * (count - 1));
}

export function scrubBaseline(points, rangeLabel, previousClose) {
	if (rangeLabel === '1D' && previousClose > 0) return previousClose;
	return points?.[0]?.close > 0 ? points[0].close : null;
}

export function formatChartTime(timeSec, rangeLabel) {
	if (!timeSec) return '';
	const d = new Date(timeSec * 1000);
	if (rangeLabel === '1D') {
		return d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
	}
	if (rangeLabel === '1W') {
		return d.toLocaleString('en-US', { weekday: 'short', hour: 'numeric', minute: '2-digit' });
	}
	if (rangeLabel === '1M' || rangeLabel === '3M') {
		return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
	}
	return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

export function quoteStats(quote) {
	return [
		{ leftLabel: 'Open', leftValue: formatNumber(quote?.open), rightLabel: 'High', rightValue: formatNumber(quote?.high) },
		{ leftLabel: 'Low', leftValue: formatNumber(quote?.low), rightLabel: 'Vol', rightValue: formatVolume(quote?.volume) },
		{ leftLabel: '52W H', leftValue: formatNumber(quote?.week52High), rightLabel: '52W L', rightValue: formatNumber(quote?.week52Low) },
	];
}

export function formatPosition(positionMs, durationMs) {
	return `${formatDuration(positionMs)} of ${formatDuration(durationMs)}`;
}

export function formatSpeed(speed) {
	const s = snapSpeed(speed);
	return Number.isInteger(s) ? `${s}×` : `${s.toFixed(1)}×`;
}

export function snapSpeed(speed) {
	return SPEED_STEPS.reduce((best, step) => (Math.abs(step - speed) < Math.abs(best - speed) ? step : best), SPEED_STEPS[1]);
}

export function timestamps(text) {
	const hits = [];
	const re = /\b(?:\d{1,2}:)?\d{1,2}:\d{2}\b/g;
	let match;
	const src = String(text || '');
	while ((match = re.exec(src))) {
		const ms = parseDuration(match[0]);
		if (ms == null) continue;
		hits.push({ start: match.index, end: match.index + match[0].length, positionMs: ms, raw: match[0] });
	}
	return hits;
}

export function podcastsOf(doc) {
	const bag = doc?.podcasts || {};
	return {
		shows: Array.isArray(bag.shows) ? bag.shows : [],
		episodes: Array.isArray(bag.episodes) ? bag.episodes : [],
		progress: Array.isArray(bag.progress) ? bag.progress : [],
		cacheBytes: bag.cacheBytes || 0,
	};
}

export function progressMap(progress) {
	const map = new Map();
	for (const row of progress || []) {
		if (row?.episodeId) map.set(row.episodeId, row);
	}
	return map;
}

export function finished(progress) {
	if (!progress) return false;
	if (progress.finished) return true;
	const duration = progress.durationMs || 0;
	if (duration <= 0) return false;
	const remaining = duration - (progress.positionMs || 0);
	return remaining <= FINISH_REMAINING_MS || progress.positionMs * 100 >= duration * 95;
}

export function skipped(progress) {
	return progress?.skipped === true;
}

export function homeRows(shows, episodes, progress, currentEpisodeId = null) {
	const showById = new Map((shows || []).map((show) => [show.feedUrl, show]));
	const byId = progressMap(progress);
	const currentId = currentEpisodeId || null;
	const continueRows = [...byId.values()]
		.filter((row) => !finished(row) && !skipped(row) && row.lastPlayedAt > 0 && row.episodeId !== currentId)
		.sort((a, b) => (b.lastPlayedAt || 0) - (a.lastPlayedAt || 0))
		.map((row) => {
			const episode = (episodes || []).find((item) => item.id === row.episodeId);
			const show = episode ? showById.get(episode.showId) : null;
			if (!episode || !show) return null;
			return { kind: 'continue', episode, show, progress: row };
		})
		.filter(Boolean)
		.slice(0, PODCAST_CONTINUE);
	const skipIds = new Set(continueRows.map((row) => row.episode.id));
	if (currentId) skipIds.add(currentId);
	const fresh = [...(episodes || [])]
		.sort((a, b) => (b.pubDate || 0) - (a.pubDate || 0))
		.map((episode) => {
			if (skipIds.has(episode.id)) return null;
			const p = byId.get(episode.id);
			if (finished(p) || skipped(p)) return null;
			const show = showById.get(episode.showId);
			if (!show) return null;
			return { kind: 'fresh', episode, show };
		})
		.filter(Boolean)
		.slice(0, PODCAST_NEW);
	const subs = [...(shows || [])]
		.sort((a, b) => String(a.title || '').localeCompare(String(b.title || ''), undefined, { sensitivity: 'base' }))
		.map((show) => ({ kind: 'subscription', show }));
	const rows = [];
	if (continueRows.length) {
		rows.push({ kind: 'header', title: 'recent' });
		rows.push(...continueRows);
	}
	if (fresh.length) {
		rows.push({ kind: 'header', title: 'next 5 episodes' });
		rows.push(...fresh);
	}
	if (subs.length) {
		rows.push({ kind: 'header', title: 'podcasts' });
		rows.push(...subs);
	}
	return rows;
}

export function formatDuration(ms) {
	const total = Math.max(0, Math.floor((ms || 0) / 1000));
	const h = Math.floor(total / 3600);
	const m = Math.floor((total % 3600) / 60);
	const s = total % 60;
	if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
	return `${m}:${String(s).padStart(2, '0')}`;
}

export function parseDuration(raw) {
	const t = String(raw || '').trim();
	if (!t) return 0;
	if (/^\d+$/.test(t)) {
		const n = Number(t);
		return n > 1000 ? n : n * 1000;
	}
	const parts = t.split(':').map((p) => Number(p));
	if (parts.some((n) => Number.isNaN(n))) return 0;
	if (parts.length === 3) return ((parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000);
	if (parts.length === 2) return ((parts[0] * 60 + parts[1]) * 1000);
	return 0;
}

export function parseItunes(raw) {
	let root;
	try {
		root = JSON.parse(raw);
	} catch {
		return [];
	}
	const results = root?.results;
	if (!Array.isArray(results)) return [];
	return results
		.map((obj) => {
			const feedUrl = String(obj?.feedUrl || '').trim();
			const title = String(obj?.collectionName || obj?.trackName || '').trim();
			if (!feedUrl || !title) return null;
			return {
				title,
				author: String(obj?.artistName || '').trim(),
				feedUrl,
				artworkUrl: String(obj?.artworkUrl600 || obj?.artworkUrl100 || '').trim(),
			};
		})
		.filter(Boolean);
}

export function parseOpml(xml) {
	const urls = [];
	const re = /<outline\b[^>]*>/gi;
	let match;
	while ((match = re.exec(xml))) {
		const tag = match[0];
		const feed = attr(tag, 'xmlUrl') || attr(tag, 'xmlurl');
		if (!feed) continue;
		urls.push({
			feedUrl: feed,
			title: attr(tag, 'text') || attr(tag, 'title') || feed,
			author: '',
			artworkUrl: '',
		});
	}
	return urls;
}

export function parseRss(xml, feedUrl) {
	const channel = inner(xml, 'channel') || xml;
	const title = text(channel, 'title');
	if (!title) return null;
	const author = text(channel, 'itunes:author') || text(channel, 'author');
	const artwork =
		attr(openTag(channel, 'itunes:image'), 'href') ||
		text(inner(channel, 'image') || '', 'url') ||
		'';
	const show = { feedUrl, title, author, artworkUrl: artwork, subscribedAt: Date.now(), episodeOrder: 'NEWEST' };
	const episodes = [];
	const itemRe = /<item\b[^>]*>([\s\S]*?)<\/item>/gi;
	let match;
	while ((match = itemRe.exec(channel))) {
		const item = match[1];
		const enclosure = openTag(item, 'enclosure');
		const url = attr(enclosure, 'url') || text(item, 'link');
		if (!url) continue;
		const guid = text(item, 'guid') || url;
		episodes.push({
			id: guid,
			showId: feedUrl,
			title: text(item, 'title') || 'untitled',
			pubDate: Date.parse(text(item, 'pubDate')) || 0,
			durationMs: parseDuration(text(item, 'itunes:duration')),
			enclosureUrl: url,
			description: text(item, 'content:encoded') || text(item, 'itunes:summary') || text(item, 'description'),
		});
	}
	return { show, episodes };
}

export function looksLikeFeedUrl(raw) {
	const t = String(raw || '').trim();
	if (!t || t.includes(' ') || t.includes('\n')) return false;
	return t.startsWith('https://') || t.startsWith('http://');
}

export function mergeFeed(existingShows, existingEpisodes, feed) {
	const shows = [...(existingShows || [])];
	const idx = shows.findIndex((show) => show.feedUrl === feed.show.feedUrl);
	const mergedShow = {
		...(idx >= 0 ? shows[idx] : {}),
		...feed.show,
		episodeOrder: idx >= 0 ? shows[idx].episodeOrder : feed.show.episodeOrder,
		subscribedAt: idx >= 0 ? shows[idx].subscribedAt : feed.show.subscribedAt,
	};
	if (idx >= 0) shows[idx] = mergedShow;
	else shows.push(mergedShow);
	const kept = (existingEpisodes || []).filter((episode) => episode.showId !== feed.show.feedUrl);
	return { shows, episodes: [...kept, ...feed.episodes] };
}

export function unsubscribe(shows, episodes, progress, feedUrl) {
	return {
		shows: (shows || []).filter((show) => show.feedUrl !== feedUrl),
		episodes: (episodes || []).filter((episode) => episode.showId !== feedUrl),
		progress: (progress || []).filter((row) => {
			const ep = (episodes || []).find((item) => item.id === row.episodeId);
			return ep && ep.showId !== feedUrl;
		}),
	};
}

export function upsertProgress(progress, next) {
	const rest = (progress || []).filter((row) => row.episodeId !== next.episodeId);
	return [...rest, next];
}

function num(value) {
	const n = Number(value);
	return Number.isFinite(n) ? n : null;
}

function attr(tag, name) {
	if (!tag) return '';
	const re = new RegExp(`${name}\\s*=\\s*["']([^"']*)["']`, 'i');
	return decode(re.exec(tag)?.[1] || '');
}

function openTag(xml, name) {
	const re = new RegExp(`<${escapeRe(name)}\\b[^>]*>`, 'i');
	return re.exec(xml)?.[0] || '';
}

function inner(xml, name) {
	const re = new RegExp(`<${escapeRe(name)}\\b[^>]*>([\\s\\S]*?)<\\/${escapeRe(name)}>`, 'i');
	return re.exec(xml)?.[1] || '';
}

function text(xml, name) {
	return decode(stripTags(inner(xml, name)).trim());
}

function stripTags(value) {
	return String(value || '')
		.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
		.replace(/<[^>]+>/g, '');
}

function decode(value) {
	return String(value || '')
		.replace(/&amp;/g, '&')
		.replace(/&lt;/g, '<')
		.replace(/&gt;/g, '>')
		.replace(/&quot;/g, '"')
		.replace(/&#39;/g, "'");
}

function escapeRe(value) {
	return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

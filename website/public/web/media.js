export const STOCKS_MAX = 100;
export const PODCAST_CONTINUE = 3;
export const PODCAST_NEW = 5;
export const FINISH_REMAINING_MS = 30_000;
export const HOME_MARK_IDLE_MS = 8_000;

export function homePodcastMark(playing, episodeLoaded, pausedForMs, idleMs = HOME_MARK_IDLE_MS) {
	if (playing && episodeLoaded) return 'PAUSE';
	if (episodeLoaded && pausedForMs != null && pausedForMs < idleMs) return 'PLAY';
	return 'HEADPHONES';
}

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
		...extendedQuote(meta),
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

export function formatRatio(value, decimals = 2) {
	if (value == null || Number.isNaN(value)) return '—';
	return value.toFixed(decimals);
}

export function formatCompact(value, prefix = '') {
	if (value == null || Number.isNaN(value)) return '—';
	const abs = Math.abs(value);
	if (abs >= 1_000_000_000_000) return `${prefix}${(value / 1_000_000_000_000).toFixed(1)}T`;
	if (abs >= 1_000_000_000) return `${prefix}${(value / 1_000_000_000).toFixed(1)}B`;
	if (abs >= 1_000_000) return `${prefix}${(value / 1_000_000).toFixed(1)}M`;
	if (abs >= 1_000) return `${prefix}${(value / 1_000).toFixed(1)}K`;
	return prefix + formatNumber(value);
}

export function formatMarketCap(value) {
	return formatCompact(value, '$');
}

export function formatYield(ratio) {
	if (ratio == null || Number.isNaN(ratio)) return '—';
	const percent = Math.abs(ratio) <= 1 ? ratio * 100 : ratio;
	return `${percent.toFixed(2)}%`;
}

export const YEAR_SEC = 365.25 * 86_400;
export const WEEK_SEC = 7 * 86_400;
export const MARKET_SYMBOL = 'SPY';

export function cagr(start, end, years) {
	if (!(start > 0) || !(end > 0) || !(years > 0)) return null;
	return (end / start) ** (1 / years) - 1;
}

export function closeBefore(points, target, maxSkewSec = 21 * 86_400) {
	let hit = null;
	for (const point of points || []) {
		if (point.time <= target && (!hit || point.time > hit.time)) hit = point;
	}
	if (!hit || target - hit.time > maxSkewSec) return null;
	return hit;
}

export function performance(points, endPrice, nowSec) {
	const list = points || [];
	const now = nowSec || list[list.length - 1]?.time || 0;
	if (list.length < 2 || !(endPrice > 0) || !(now > 0)) return { y1: null, y3: null, y5: null, y10: null };
	const at = (years) => {
		const target = now - years * YEAR_SEC;
		const start = closeBefore(list, target);
		if (!start) return null;
		const actualYears = (now - start.time) / YEAR_SEC;
		if (actualYears < years * 0.85) return null;
		const rate = cagr(start.close, endPrice, actualYears);
		return rate == null ? null : rate * 100;
	};
	return { y1: at(1), y3: at(3), y5: at(5), y10: at(10) };
}

export function weeklyReturns(points) {
	const sorted = [...(points || [])].sort((a, b) => a.time - b.time);
	const out = new Map();
	for (let i = 1; i < sorted.length; i++) {
		const prev = sorted[i - 1].close;
		const cur = sorted[i].close;
		if (prev > 0) out.set(Math.floor(sorted[i].time / WEEK_SEC), cur / prev - 1);
	}
	return out;
}

export function beta(stock, market, nowSec) {
	const now = nowSec || stock?.[stock.length - 1]?.time || 0;
	if ((stock || []).length < 30 || (market || []).length < 30 || !(now > 0)) return null;
	const cutoff = now - 5 * YEAR_SEC;
	const stockRet = weeklyReturns(stock.filter((p) => p.time >= cutoff));
	const marketRet = weeklyReturns(market.filter((p) => p.time >= cutoff));
	const keys = [...stockRet.keys()].filter((k) => marketRet.has(k)).sort((a, b) => a - b);
	if (keys.length < 26) return null;
	const xs = keys.map((k) => marketRet.get(k));
	const ys = keys.map((k) => stockRet.get(k));
	const xMean = xs.reduce((a, b) => a + b, 0) / xs.length;
	const yMean = ys.reduce((a, b) => a + b, 0) / ys.length;
	let cov = 0;
	let varX = 0;
	for (let i = 0; i < xs.length; i++) {
		const dx = xs[i] - xMean;
		cov += dx * (ys[i] - yMean);
		varX += dx * dx;
	}
	if (varX === 0) return null;
	return cov / varX;
}

export function avgVolume(volumes) {
	if (!volumes?.length) return null;
	return Math.round(volumes.reduce((a, b) => a + b, 0) / volumes.length);
}

export function formatExtended(quote) {
	const label = String(quote?.extendedLabel || '').trim();
	const extendedPrice = quote?.extendedPrice;
	if (!label || extendedPrice == null) return '';
	const price = formatPrice(extendedPrice, quote.currency);
	const change = quote.extendedChange;
	const percent = quote.extendedPercent;
	if (change != null && percent != null) {
		return `${label} ${price} ${formatChange(change)} (${formatPercent(percent)})`;
	}
	return `${label} ${price}`;
}

export function parseTimeseries(raw) {
	let root;
	try {
		root = JSON.parse(raw);
	} catch {
		return {};
	}
	const result = root?.timeseries?.result;
	if (!Array.isArray(result)) return {};
	const values = {};
	for (const obj of result) {
		if (!obj || typeof obj !== 'object') continue;
		const type =
			obj.meta?.type?.[0] ||
			Object.keys(obj).find((key) => key !== 'meta' && key !== 'timestamp');
		if (!type) continue;
		const n = latestNumber(obj);
		if (n != null) values[type] = n;
	}
	return {
		pe: values.trailingPeRatio ?? null,
		marketCap: values.trailingMarketCap ?? null,
		dividendYield: values.trailingDividendYield ?? null,
		eps: values.trailingDilutedEPS ?? null,
	};
}

export function quoteStats(quote) {
	return [
		{ leftLabel: 'Open', leftValue: formatNumber(quote?.open), rightLabel: 'High', rightValue: formatNumber(quote?.high) },
		{ leftLabel: 'Low', leftValue: formatNumber(quote?.low), rightLabel: 'Vol', rightValue: formatVolume(quote?.volume) },
		{ leftLabel: 'P/E', leftValue: formatRatio(quote?.pe), rightLabel: 'Mkt Cap', rightValue: formatMarketCap(quote?.marketCap) },
		{ leftLabel: 'EPS', leftValue: formatRatio(quote?.eps), rightLabel: 'Yield', rightValue: formatYield(quote?.dividendYield) },
		{ leftLabel: 'Beta', leftValue: formatRatio(quote?.beta), rightLabel: 'Avg Vol', rightValue: quote?.avgVolume != null ? formatVolume(quote.avgVolume) : '—' },
		{ leftLabel: '52W H', leftValue: formatNumber(quote?.week52High), rightLabel: '52W L', rightValue: formatNumber(quote?.week52Low) },
	];
}

export function cagrStats(cagrRow) {
	const row = cagrRow || {};
	return [
		{ leftLabel: '1Y', leftValue: row.y1 != null ? formatPercent(row.y1) : '—', rightLabel: '3Y', rightValue: row.y3 != null ? formatPercent(row.y3) : '—' },
		{ leftLabel: '5Y', leftValue: row.y5 != null ? formatPercent(row.y5) : '—', rightLabel: '10Y', rightValue: row.y10 != null ? formatPercent(row.y10) : '—' },
	];
}

function latestNumber(obj) {
	const key = Object.keys(obj).find((k) => k !== 'meta' && k !== 'timestamp');
	if (!key || !Array.isArray(obj[key]) || !obj[key].length) return null;
	const last = obj[key][obj[key].length - 1];
	if (!last || typeof last !== 'object') return null;
	return num(last.reportedValue?.raw) ?? num(last.dataValue) ?? num(last.raw);
}

function extendedQuote(meta) {
	const nowSec = Math.floor(Date.now() / 1000);
	const label = extendedSession(nowSec, meta?.hasPrePostMarketData === true, meta?.currentTradingPeriod);
	const extendedPrice = label ? num(meta?.fulldayPrice) : null;
	if (!label || extendedPrice == null) return {};
	return {
		extendedLabel: label,
		extendedPrice,
		extendedChange: num(meta?.fulldayChange),
		extendedPercent: num(meta?.fulldayChangePercent),
	};
}

function extendedSession(nowSec, hasPrePost, periods) {
	if (!hasPrePost || !periods) return null;
	const preStart = num(periods.pre?.start);
	const preEnd = num(periods.pre?.end);
	const regStart = num(periods.regular?.start);
	const regEnd = num(periods.regular?.end);
	const postStart = num(periods.post?.start);
	const postEnd = num(periods.post?.end);
	if (preStart != null && preEnd != null && nowSec >= preStart && nowSec <= preEnd) return 'Pre-Market';
	if (postStart != null && postEnd != null && nowSec >= postStart && nowSec <= postEnd) return 'After Hours';
	if (regStart != null && regEnd != null && nowSec >= regStart && nowSec <= regEnd) return null;
	if (regEnd != null && nowSec > regEnd) return 'After Hours';
	if (preStart != null && nowSec < preStart) return 'After Hours';
	return null;
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

export const FETCH_TIMEOUT_MS = 12_000;
export const FEED_HYDRATE_LIMIT = 4;

export function podcastsOf(doc) {
	const bag = doc?.podcasts || {};
	return {
		shows: Array.isArray(bag.shows) ? bag.shows : [],
		episodes: Array.isArray(bag.episodes) ? bag.episodes : [],
		progress: Array.isArray(bag.progress) ? bag.progress : [],
		cacheBytes: bag.cacheBytes || 0,
	};
}

/** How fresh a subscribed show's episode list must stay (matches the phone feed cache). */
export const FEED_STALE_MS = 30 * 60_000;

/**
 * Account snapshots omit episode catalogs, and the browser only keeps a slim
 * copy — so a show with any cached episodes is still stale once FEED_STALE_MS
 * passes. Empty shows always hydrate; non-empty shows re-check when their
 * lastCheckedAt (or the snapshot export time) is older than the window.
 * Unknown ages count as stale so the first check after sync or reload refreshes.
 */
export function showsNeedingFeed(bag, now = Date.now(), staleMs = FEED_STALE_MS) {
	const shows = bag?.shows || [];
	const have = new Set((bag?.episodes || []).map((ep) => String(ep.showId || '').toLowerCase()));
	return shows.filter((show) => {
		const feedUrl = String(show.feedUrl || '').toLowerCase();
		if (!have.has(feedUrl)) return true;
		const last = Number(show.lastCheckedAt) || Number(bag?.exportedAt) || 0;
		return !last || now - last >= staleMs;
	});
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

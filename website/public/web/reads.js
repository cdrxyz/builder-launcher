import { formatPosition } from './media.js';

export const MAX_ARTICLES = 100;
export const MAX_TEXT = 40_000;
export const WPM = 160;
export const SKIP_MS = 15_000;
const FNV_OFFSET = 0xcbf29ce484222325n;
const FNV_PRIME = 0x100000001b3n;
const FNV_MASK = 0xffffffffffffffffn;
const PRIVATE =
	/^(localhost|127\.0\.0\.1|0\.0\.0\.0|10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[0-1])\.\d+\.\d+|169\.254\.\d+\.\d+|.*\.local|metadata\.google\.internal)$/i;

export function fnv1a64(text) {
	let hash = FNV_OFFSET;
	for (const b of new TextEncoder().encode(text)) {
		hash ^= BigInt(b);
		hash = (hash * FNV_PRIME) & FNV_MASK;
	}
	return hash.toString(16).padStart(16, '0');
}

export function publicUrl(raw) {
	let text = String(raw || '').trim();
	if (!text || /\s/.test(text)) return null;
	if (!/^[a-z][a-z0-9+.-]*:/i.test(text)) {
		if (!text.includes('.') || text.startsWith('/') || text.startsWith('.')) return null;
		text = `https://${text}`;
	}
	let url;
	try {
		url = new URL(text);
	} catch {
		return null;
	}
	if (url.protocol !== 'https:' && url.protocol !== 'http:') return null;
	if (url.username || url.password) return null;
	const host = url.hostname.toLowerCase();
	if (host.includes(':') || PRIVATE.test(host)) return null;
	return url;
}

export function fetchUrl(raw) {
	const url = publicUrl(raw);
	if (!url) return null;
	url.hash = '';
	url.hostname = url.hostname.toLowerCase();
	if ((url.protocol === 'https:' && url.port === '443') || (url.protocol === 'http:' && url.port === '80')) {
		url.port = '';
	}
	return url.toString();
}

export function nextFetchUrl(current, location) {
	let next;
	try {
		next = new URL(location, current).toString();
	} catch {
		return null;
	}
	return fetchUrl(next);
}

export function normalizeUrl(raw) {
	const text = fetchUrl(raw);
	if (!text) return null;
	const url = new URL(text);
	if (url.pathname.length > 1 && url.pathname.endsWith('/')) url.pathname = url.pathname.slice(0, -1);
	return url.toString();
}

export function articleId(raw) {
	const key = normalizeUrl(raw);
	return key ? `r${fnv1a64(key)}` : null;
}

export function publicationFromUrl(raw) {
	const url = publicUrl(raw);
	return url ? url.hostname.toLowerCase().replace(/^www\./, '') : '';
}

export function indexFromInput(raw, count) {
	const q = String(raw || '').trim();
	if (!q || !/^\d+$/.test(q)) return null;
	const n = Number(q);
	if (n < 1 || n > count) return null;
	return n - 1;
}

export function wordCount(text) {
	return String(text || '')
		.split(/\s+/)
		.filter((word) => /[0-9A-Za-z]/.test(word)).length;
}

export function durationMs(text) {
	const words = wordCount(text);
	if (words <= 0) return 0;
	return Math.floor((words * 60_000) / WPM);
}

export function positionLabel(positionMs, totalMs) {
	if (!totalMs || totalMs <= 0) return '';
	const pos = Math.min(Math.max(0, positionMs || 0), totalMs);
	return formatPosition(pos, totalMs);
}

export function sorted(articles) {
	return [...(articles || [])].sort((a, b) => (b.addedAt || 0) - (a.addedAt || 0));
}

export function addArticle(articles, article, now) {
	const prev = (articles || []).find((item) => item.id === article.id);
	const merged = prev
		? {
				...article,
				addedAt: now,
				title: article.title || prev.title,
				publication: article.publication || prev.publication,
				text: article.text || prev.text,
			}
		: { ...article, addedAt: now };
	const next = sorted([merged, ...(articles || []).filter((item) => item.id !== merged.id)]);
	if (next.length <= MAX_ARTICLES) return { articles: next, droppedIds: [] };
	return { articles: next.slice(0, MAX_ARTICLES), droppedIds: next.slice(MAX_ARTICLES).map((item) => item.id) };
}

export function removeArticle(articles, id) {
	return (articles || []).filter((item) => item.id !== id);
}

export function chunks(text) {
	const spans = sentenceSpans(text);
	if (!spans.length) return [];
	const words = spans.map((span) => Math.max(1, wordCount(text.slice(span.start, span.end))));
	const total = words.reduce((sum, n) => sum + n, 0) || 1;
	const dur = Math.max(1, durationMs(text));
	let cursor = 0;
	return spans.map((span, index) => {
		const slice = index === spans.length - 1 ? dur - cursor : Math.floor((dur * words[index]) / total);
		const chunk = { start: span.start, end: span.end, startMs: cursor, endMs: cursor + slice };
		cursor += slice;
		return chunk;
	});
}

export function chunkIndexAt(rows, positionMs) {
	if (!rows.length) return 0;
	const pos = Math.max(0, positionMs || 0);
	const hit = rows.findIndex((row) => pos < row.endMs);
	return hit < 0 ? rows.length - 1 : hit;
}

export function skipMs(positionMs, totalMs, deltaMs) {
	const cap = Math.max(0, totalMs || 0);
	return Math.min(cap, Math.max(0, (positionMs || 0) + deltaMs));
}

export function playFraction(positionMs, totalMs) {
	if (!totalMs || totalMs <= 0) return 0;
	return Math.min(1, Math.max(0, (positionMs || 0) / totalMs));
}

export function msAt(frac, totalMs) {
	if (!totalMs || totalMs <= 0) return 0;
	const t = Math.min(1, Math.max(0, frac || 0));
	return Math.floor(t * totalMs);
}

export function readsOf(doc) {
	const bag = doc?.reads || {};
	return {
		articles: bag.articles || [],
		progress: bag.progress || [],
		deletedIds: bag.deletedIds || [],
	};
}

export function progressFor(progress, id) {
	return (progress || []).find((row) => row.articleId === id) || null;
}

export function upsertReadProgress(progress, row) {
	const rest = (progress || []).filter((item) => item.articleId !== row.articleId);
	return [...rest, row];
}

export function deletedIdsAfterAdd(deletedIds, articleId, droppedIds = []) {
	const id = String(articleId || '');
	const out = new Set([...(deletedIds || []), ...(droppedIds || [])].map(String));
	if (id) out.delete(id);
	return [...out];
}

export function mergeReads(a, b, opts = {}) {
	const left = a || {};
	const right = b || {};
	const restored = new Set((opts.restoredIds || []).map(String));
	const deletedIds = [...new Set([...(left.deletedIds || []), ...(right.deletedIds || [])].map(String))]
		.filter((id) => !restored.has(id));
	const drop = new Set(deletedIds);
	const articles = new Map();
	for (const item of [...(left.articles || []), ...(right.articles || [])]) {
		if (!item?.id || drop.has(String(item.id))) continue;
		articles.set(item.id, mergeArticle(articles.get(item.id), item));
	}
	const progress = new Map();
	for (const row of [...(left.progress || []), ...(right.progress || [])]) {
		const id = String(row?.articleId || '');
		if (!id || drop.has(id) || !articles.has(id)) continue;
		progress.set(id, mergeReadProgress(progress.get(id), row));
	}
	return {
		articles: sorted([...articles.values()]),
		progress: [...progress.values()],
		deletedIds,
	};
}

function mergeArticle(prev, item) {
	if (!prev) return item;
	const newer = (item.addedAt || 0) >= (prev.addedAt || 0) ? item : prev;
	const older = newer === item ? prev : item;
	const text = (item.text || '').length >= (prev.text || '').length ? item.text || '' : prev.text || '';
	return {
		...newer,
		title: newer.title || older.title || '',
		publication: newer.publication || older.publication || '',
		text: text || older.text || '',
	};
}

function mergeReadProgress(prev, row) {
	if (!prev) return row;
	const nextAt = row.updatedAt || 0;
	const prevAt = prev.updatedAt || 0;
	if (nextAt > prevAt) return row;
	if (nextAt < prevAt) return prev;
	return (row.positionMs || 0) >= (prev.positionMs || 0) ? row : prev;
}

export function extractArticle(html, pageUrl) {
	const source = String(html || '');
	const title = meta(source, 'og:title') || meta(source, 'twitter:title') || elementText(source, 'title') || '';
	const publication = meta(source, 'og:site_name') || meta(source, 'application-name') || meta(source, 'publisher') || publicationFromUrl(pageUrl);
	const region = elementInner(source, 'article') || elementInner(source, 'main') || elementInner(source, 'body') || source;
	const text = plain(region).slice(0, MAX_TEXT);
	if (text.length < 40) return null;
	return {
		title: (title || text.split('\n')[0] || '').trim().slice(0, 180),
		publication: publication || publicationFromUrl(pageUrl),
		text,
	};
}

function meta(html, key) {
	const tags = html.match(/<meta\b[^>]*>/gi) || [];
	for (const tag of tags) {
		const name = attr(tag, 'property') || attr(tag, 'name');
		if (!name || name.toLowerCase() !== key.toLowerCase()) continue;
		const content = decode(attr(tag, 'content') || '').trim();
		if (content) return content;
	}
	return '';
}

function elementText(html, tag) {
	const inner = elementInner(html, tag);
	return inner ? plain(inner) : '';
}

function elementInner(html, tag) {
	const re = new RegExp(`<${tag}\\b[^>]*>([\\s\\S]*?)</${tag}>`, 'i');
	const match = html.match(re);
	return match ? match[1] : '';
}

function attr(tag, name) {
	const re = new RegExp(`\\b${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)')`, 'i');
	const match = tag.match(re);
	if (!match) return '';
	return match[1] || match[2] || '';
}

function plain(html) {
	let body = String(html || '').replace(
		/<(script|style|nav|header|footer|aside|form|noscript|svg|iframe|button)\b[^>]*>[\s\S]*?<\/\1>/gi,
		' ',
	);
	body = body.replace(/<br\s*\/?>/gi, '\n');
	body = body.replace(/<\/(p|div|h[1-6]|li|blockquote|tr|section|article|figcaption)>/gi, '\n');
	body = body.replace(/<[^>]+>/g, ' ');
	return decode(body)
		.split('\n')
		.map((line) => line.replace(/\s+/g, ' ').trim())
		.filter(Boolean)
		.join('\n');
}

function decode(raw) {
	return String(raw || '')
		.replace(/&nbsp;/g, ' ')
		.replace(/&amp;/g, '&')
		.replace(/&lt;/g, '<')
		.replace(/&gt;/g, '>')
		.replace(/&quot;/g, '"')
		.replace(/&#39;/g, "'")
		.replace(/&apos;/g, "'")
		.replace(/&#(x?[0-9a-fA-F]+);/g, (all, token) => {
			const code = token[0] === 'x' || token[0] === 'X' ? Number.parseInt(token.slice(1), 16) : Number(token);
			if (!Number.isFinite(code) || code < 1 || code > 0x10ffff) return all;
			return String.fromCodePoint(code);
		});
}

function sentenceSpans(text) {
	const spans = [];
	let start = 0;
	let i = 0;
	while (i < text.length) {
		const c = text[i];
		const boundary = c === '\n' || ((c === '.' || c === '!' || c === '?') && (i + 1 >= text.length || /\s/.test(text[i + 1])));
		if (boundary) {
			const end = c === '\n' ? i : i + 1;
			if (/[0-9A-Za-z]/.test(text.slice(start, end))) spans.push({ start, end });
			let next = end;
			while (next < text.length && /\s/.test(text[next])) next += 1;
			start = next;
			i = next;
			continue;
		}
		i += 1;
	}
	if (start < text.length && /[0-9A-Za-z]/.test(text.slice(start))) spans.push({ start, end: text.length });
	return spans.flatMap((span) => splitLong(text, span.start, span.end));
}

function splitLong(text, start, end) {
	if (end - start <= 240) return [{ start, end }];
	const window = text.slice(start, end);
	const space = window.lastIndexOf(' ', 240);
	const cut = space > 40 ? space : 240;
	const mid = start + cut;
	return [...splitLong(text, start, mid), ...splitLong(text, mid, end)];
}

import { decrypt, encrypt, decodeUtf8, encodeUtf8 } from './crypto.js';
import { credentialsReady, ready, s3Get, s3Put } from './s3.js';
import {
	doneTodos,
	newId,
	noteTitle,
	notesByEdited,
	openTodos,
	seedNote,
} from './items.js';
import { renderMarkdown } from './markdown.js';
import {
	addTicker,
	finished,
	formatDuration,
	formatPercent,
	formatPrice,
	homeRows,
	looksLikeFeedUrl,
	looksLikeSymbol,
	mergeFeed,
	parseItunes,
	parseOpml,
	parseRss,
	parseYahooChart,
	parseYahooSearch,
	podcastsOf,
	progressMap,
	removeTicker,
	skipped,
	unsubscribe,
	upsertProgress,
	watchlist,
} from './media.js';

const CREDS_KEY = 'builder-launcher-web-creds';
const SNAP_KEY = 'builder-launcher-web-snapshot';
const DOCS = 'https://cdrxyz.github.io/builder-launcher/configure/web/';
const YAHOO_CHART = 'https://query1.finance.yahoo.com/v8/finance/chart';
const YAHOO_SEARCH = 'https://query1.finance.yahoo.com/v1/finance/search';
const ITUNES = 'https://itunes.apple.com/search';

const audio = new Audio();
let lastSavedAt = 0;
let lastSavedPos = 0;

const state = {
	tab: 'tasks',
	noteId: null,
	noteMode: 'edit',
	showId: null,
	episodeId: null,
	status: '',
	busy: false,
	dirty: false,
	hits: [],
	quotes: {},
	creds: loadCreds(),
	doc: loadSnapshot(),
};

const app = document.getElementById('app');

if ('serviceWorker' in navigator) {
	navigator.serviceWorker.register('./sw.js').catch(() => {});
}

audio.addEventListener('timeupdate', () => checkpoint(false));
audio.addEventListener('pause', () => checkpoint(true));
audio.addEventListener('ended', () => {
	checkpoint(true, true);
	render();
});

if (!readyCreds(state.creds) && !state.doc) state.tab = 'settings';
render();
if (readyCreds(state.creds) && !state.doc) pull().catch(() => {});
else if (state.doc) refreshQuotes();

function loadCreds() {
	try {
		return {
			endpoint: '',
			bucket: '',
			accessKey: '',
			secretKey: '',
			encryptionKey: '',
			...JSON.parse(localStorage.getItem(CREDS_KEY) || '{}'),
		};
	} catch {
		return { endpoint: '', bucket: '', accessKey: '', secretKey: '', encryptionKey: '' };
	}
}

function saveCreds(creds) {
	localStorage.setItem(CREDS_KEY, JSON.stringify(creds));
}

function loadSnapshot() {
	try {
		return JSON.parse(localStorage.getItem(SNAP_KEY) || 'null');
	} catch {
		return null;
	}
}

function saveSnapshot(doc) {
	localStorage.setItem(SNAP_KEY, JSON.stringify(doc));
}

function readyCreds(creds) {
	return ready(creds.endpoint, creds.bucket, creds.accessKey, creds.secretKey, creds.encryptionKey);
}

function items() {
	return state.doc?.items || [];
}

function ensureDoc() {
	if (!state.doc) state.doc = { version: 1, exportedAt: Date.now(), items: [], watchlist: [], podcasts: { shows: [], episodes: [], progress: [] } };
}

function mutate(next) {
	ensureDoc();
	state.doc = { ...state.doc, ...next };
	state.dirty = true;
	saveSnapshot(state.doc);
	render();
}

function setItems(next) {
	mutate({ items: next });
}

function setStatus(message) {
	state.status = message;
	render();
}

async function pull() {
	if (!readyCreds(state.creds)) {
		setStatus('Set endpoint, bucket, keys, and encryption key.');
		return;
	}
	state.busy = true;
	setStatus('Pulling…');
	try {
		const blob = await s3Get(state.creds);
		const plain = await decrypt(blob, state.creds.encryptionKey);
		state.doc = JSON.parse(decodeUtf8(plain));
		state.dirty = false;
		state.hits = [];
		saveSnapshot(state.doc);
		state.status = pulledLabel(state.doc);
		refreshQuotes();
	} catch (err) {
		state.status = err.message || String(err);
	} finally {
		state.busy = false;
		render();
	}
}

async function push() {
	if (!readyCreds(state.creds) || !state.doc) {
		setStatus('Pull a backup before pushing.');
		return;
	}
	if (
		!window.confirm(
			'Upload this snapshot? The last successful upload wins. The phone will see these tasks, notes, stocks, and podcasts on restore.',
		)
	) {
		return;
	}
	state.busy = true;
	setStatus('Pushing…');
	try {
		const next = { ...state.doc, exportedAt: Date.now() };
		const blob = await encrypt(encodeUtf8(`${JSON.stringify(next, null, 2)}\n`), state.creds.encryptionKey);
		await s3Put(state.creds, blob);
		state.doc = next;
		state.dirty = false;
		saveSnapshot(state.doc);
		state.status = `Pushed ${new Date(next.exportedAt).toLocaleString()}`;
	} catch (err) {
		state.status = err.message || String(err);
	} finally {
		state.busy = false;
		render();
	}
}

function pulledLabel(doc) {
	const when = doc?.exportedAt ? new Date(doc.exportedAt).toLocaleString() : 'unknown time';
	const todos = openTodos(doc.items).length;
	const notes = notesByEdited(doc.items).length;
	const stocks = watchlist(doc).length;
	const shows = podcastsOf(doc).shows.length;
	return `Pulled ${when} · ${todos} tasks · ${notes} notes · ${stocks} stocks · ${shows} shows`;
}

function render() {
	app.replaceChildren();
	app.append(header(), statusLine(), main(), tabs());
}

function header() {
	const el = document.createElement('header');
	el.className = 'app-bar';
	if (state.tab === 'note') {
		el.append(button('<', () => go('notes'), 'ghost'));
		el.append(title('note'));
		el.append(
			button(state.noteMode === 'edit' ? 'view' : 'edit', () => {
				state.noteMode = state.noteMode === 'edit' ? 'view' : 'edit';
				render();
			}, 'ghost'),
		);
		return el;
	}
	if (state.tab === 'show') {
		el.append(button('<', () => go('pods'), 'ghost'));
		el.append(title('show'));
		el.append(button(state.dirty ? 'push*' : 'push', () => push(), 'ghost'));
		return el;
	}
	if (state.tab === 'episode') {
		el.append(button('<', () => (state.showId ? go('show') : go('pods')), 'ghost'));
		el.append(title('episode'));
		el.append(button(state.dirty ? 'push*' : 'push', () => push(), 'ghost'));
		return el;
	}
	el.append(button(state.busy ? '…' : 'pull', () => pull(), 'ghost'));
	el.append(title(state.tab === 'pods' ? 'podcasts' : state.tab));
	el.append(button(state.dirty ? 'push*' : 'push', () => push(), 'ghost'));
	return el;
}

function title(text) {
	const h = document.createElement('h1');
	h.className = 'screen-title';
	h.textContent = text;
	return h;
}

function statusLine() {
	const p = document.createElement('p');
	p.className = 'status';
	p.textContent = state.status;
	return p;
}

function main() {
	const el = document.createElement('main');
	if (state.tab === 'settings') el.append(settingsScreen());
	else if (state.tab === 'notes') el.append(notesScreen());
	else if (state.tab === 'note') el.append(noteScreen());
	else if (state.tab === 'stocks') el.append(stocksScreen());
	else if (state.tab === 'pods') el.append(podsScreen());
	else if (state.tab === 'show') el.append(showScreen());
	else if (state.tab === 'episode') el.append(episodeScreen());
	else el.append(tasksScreen());
	return el;
}

function tabs() {
	const el = document.createElement('footer');
	el.className = 'tab-bar';
	const active = (name) => {
		if (name === 'notes' && state.tab === 'note') return true;
		if (name === 'pods' && (state.tab === 'show' || state.tab === 'episode')) return true;
		return state.tab === name;
	};
	const labels = [
		['tasks', 'tasks'],
		['notes', 'notes'],
		['stocks', 'stocks'],
		['pods', 'pods'],
		['settings', 'set'],
	];
	for (const [name, label] of labels) {
		el.append(
			button(label, () => go(name), `tab${active(name) ? ' active' : ''}`),
		);
	}
	return el;
}

function go(tab) {
	state.tab = tab;
	state.hits = [];
	if (tab !== 'note') state.noteId = null;
	if (tab !== 'show' && tab !== 'episode') state.showId = tab === 'pods' ? null : state.showId;
	if (tab !== 'episode') state.episodeId = tab === 'show' ? state.episodeId : null;
	render();
}

function tasksScreen() {
	const wrap = document.createElement('div');
	const open = openTodos(items());
	const done = doneTodos(items());
	if (!open.length && !done.length) wrap.append(empty('No tasks yet. Pull from S3 or type below.'));
	for (const item of open) wrap.append(todoRow(item, false));
	if (done.length) {
		wrap.append(section('done'));
		for (const item of done) wrap.append(todoRow(item, true));
	}
	wrap.append(composer('-', 'buy milk', (text) => addItem('todo', text)));
	return wrap;
}

function todoRow(item, done) {
	const row = document.createElement('button');
	row.className = `row${done ? ' done' : ''}`;
	row.type = 'button';
	const mark = document.createElement('span');
	mark.className = 'mark';
	mark.textContent = done ? '×' : '·';
	const body = document.createElement('span');
	body.className = 'body';
	body.textContent = item.text;
	row.append(mark, body);
	row.addEventListener('click', () => toggleTodo(item.id));
	row.addEventListener('contextmenu', (event) => {
		event.preventDefault();
		removeItem(item.id);
	});
	return row;
}

function notesScreen() {
	const wrap = document.createElement('div');
	const notes = notesByEdited(items());
	if (!notes.length) wrap.append(empty('No notes yet. Pull from S3 or type below.'));
	for (const item of notes) {
		const row = document.createElement('button');
		row.className = 'row';
		row.type = 'button';
		const body = document.createElement('span');
		body.className = 'body';
		body.textContent = noteTitle(item.text);
		const meta = document.createElement('span');
		meta.className = 'meta';
		meta.textContent = item.updatedAt || item.createdAt
			? new Date(item.updatedAt || item.createdAt).toLocaleDateString()
			: '';
		row.append(body, meta);
		row.addEventListener('click', () => {
			state.tab = 'note';
			state.noteId = item.id;
			state.noteMode = 'view';
			render();
		});
		wrap.append(row);
	}
	wrap.append(composer('+', 'note title', (text) => addNote(text)));
	return wrap;
}

function noteScreen() {
	const item = items().find((entry) => entry.id === state.noteId);
	const wrap = document.createElement('div');
	wrap.className = 'note-editor';
	if (!item) {
		wrap.append(empty('Note missing.'));
		return wrap;
	}
	if (state.noteMode === 'view') {
		const preview = document.createElement('div');
		preview.className = 'note-preview';
		preview.innerHTML = renderMarkdown(item.text);
		wrap.append(preview);
		return wrap;
	}
	const area = document.createElement('textarea');
	area.value = item.text;
	area.addEventListener('input', () => updateNote(item.id, area.value));
	wrap.append(area);
	return wrap;
}

function stocksScreen() {
	const wrap = document.createElement('div');
	const list = watchlist(state.doc);
	if (!list.length && !state.hits.length) wrap.append(empty('No tickers yet. Pull from S3 or type $AAPL.'));
	for (const item of list) {
		const live = state.quotes[String(item.symbol).toUpperCase()];
		const price = live?.price ?? item.price;
		const pct = live?.changePercent ?? item.changePercent;
		const row = document.createElement('button');
		row.className = 'row';
		row.type = 'button';
		const body = document.createElement('span');
		body.className = 'body';
		body.textContent = `${item.symbol}  ${item.name || ''}`;
		const meta = document.createElement('span');
		meta.className = `meta${pct == null ? '' : pct >= 0 ? ' up' : ' down'}`;
		meta.textContent = [formatPrice(price, live?.currency || item.currency), formatPercent(pct)]
			.filter(Boolean)
			.join('  ');
		row.append(body, meta);
		row.addEventListener('contextmenu', (event) => {
			event.preventDefault();
			mutate({ watchlist: removeTicker(watchlist(state.doc), item.symbol) });
		});
		wrap.append(row);
	}
	for (const hit of state.hits) {
		const row = document.createElement('button');
		row.className = 'row';
		row.type = 'button';
		const body = document.createElement('span');
		body.className = 'body';
		body.textContent = `${hit.symbol}  ${hit.name}`;
		const meta = document.createElement('span');
		meta.className = 'meta';
		meta.textContent = hit.exchange || 'add';
		row.append(body, meta);
		row.addEventListener('click', () => addStock(hit));
		wrap.append(row);
	}
	wrap.append(
		composer('$', 'AAPL', async (text) => {
			await searchOrAddStock(text);
		}),
	);
	return wrap;
}

function podsScreen() {
	const wrap = document.createElement('div');
	const bag = podcastsOf(state.doc);
	if (state.hits.length) {
		wrap.append(section('search'));
		for (const hit of state.hits) wrap.append(podcastHitRow(hit));
	}
	const rows = homeRows(bag.shows, bag.episodes, bag.progress, playingId());
	if (!rows.length && !state.hits.length) {
		wrap.append(empty('No shows yet. Pull from S3, search a name, or paste an RSS / OPML URL.'));
	}
	for (const row of rows) {
		if (row.kind === 'header') {
			wrap.append(section(row.title));
			continue;
		}
		if (row.kind === 'subscription') {
			wrap.append(showRow(row.show));
			continue;
		}
		wrap.append(episodeRow(row.episode, row.show, row.progress));
	}
	wrap.append(
		composer('', 'show, RSS, or OPML', async (text) => {
			await searchOrSubscribe(text);
		}),
	);
	return wrap;
}

function showScreen() {
	const wrap = document.createElement('div');
	const bag = podcastsOf(state.doc);
	const show = bag.shows.find((item) => item.feedUrl === state.showId);
	if (!show) {
		wrap.append(empty('Show missing.'));
		return wrap;
	}
	wrap.append(section(show.title));
	const byId = progressMap(bag.progress);
	const newest = show.episodeOrder !== 'OLDEST';
	const list = bag.episodes
		.filter((item) => item.showId === show.feedUrl)
		.sort((a, b) => (newest ? (b.pubDate || 0) - (a.pubDate || 0) : (a.pubDate || 0) - (b.pubDate || 0)));
	if (!list.length) wrap.append(empty('No episodes in the snapshot. RSS refresh needs CORS on the feed.'));
	for (const episode of list) wrap.append(episodeRow(episode, show, byId.get(episode.id)));
	return wrap;
}

function episodeScreen() {
	const wrap = document.createElement('div');
	wrap.className = 'player';
	const bag = podcastsOf(state.doc);
	const episode = bag.episodes.find((item) => item.id === state.episodeId);
	if (!episode) {
		wrap.append(empty('Episode missing.'));
		return wrap;
	}
	const show = bag.shows.find((item) => item.feedUrl === episode.showId);
	const h = document.createElement('h2');
	h.textContent = episode.title;
	const sub = document.createElement('p');
	sub.className = 'hint';
	sub.textContent = show?.title || '';
	wrap.append(h, sub);
	if (episode.enclosureUrl) {
		if (audio.src !== episode.enclosureUrl) {
			const p = progressMap(bag.progress).get(episode.id);
			audio.src = episode.enclosureUrl;
			if (p?.positionMs) audio.currentTime = p.positionMs / 1000;
		}
		const controls = document.createElement('div');
		controls.className = 'actions';
		controls.append(
			button(audio.paused ? 'play' : 'pause', () => togglePlay(episode), 'primary'),
			button('−15', () => {
				audio.currentTime = Math.max(0, audio.currentTime - 15);
				checkpoint(true);
			}, 'ghost'),
			button('+15', () => {
				audio.currentTime = audio.currentTime + 15;
				checkpoint(true);
			}, 'ghost'),
		);
		const pos = document.createElement('p');
		pos.className = 'hint';
		pos.id = 'play-pos';
		const durMs =
			Number.isFinite(audio.duration) && audio.duration > 0 ? audio.duration * 1000 : episode.durationMs || 0;
		pos.textContent = `${formatDuration(audio.currentTime * 1000)} / ${formatDuration(durMs)}`;
		wrap.append(controls, pos);
	} else {
		wrap.append(empty('No audio URL in this episode.'));
	}
	if (episode.description) {
		const notes = document.createElement('div');
		notes.className = 'note-preview';
		notes.innerHTML = renderMarkdown(episode.description.replace(/<[^>]+>/g, ''));
		wrap.append(notes);
	}
	return wrap;
}

function settingsScreen() {
	const form = document.createElement('form');
	form.className = 'settings';
	form.addEventListener('submit', (event) => {
		event.preventDefault();
		const data = new FormData(form);
		state.creds = {
			endpoint: String(data.get('endpoint') || '').trim(),
			bucket: String(data.get('bucket') || '').trim(),
			accessKey: String(data.get('accessKey') || '').trim(),
			secretKey: String(data.get('secretKey') || '').trim(),
			encryptionKey: String(data.get('encryptionKey') || ''),
		};
		saveCreds(state.creds);
		setStatus(
			credentialsReady(state.creds.endpoint, state.creds.bucket, state.creds.accessKey, state.creds.secretKey)
				? 'Saved on this device. Pull to load the snapshot.'
				: 'Need endpoint, bucket, access key, and secret key.',
		);
		if (readyCreds(state.creds)) pull();
	});
	form.append(
		field('Endpoint', 'endpoint', state.creds.endpoint, 'https://….r2.cloudflarestorage.com'),
		field('Bucket', 'bucket', state.creds.bucket, 'my-bucket'),
		field('Access key', 'accessKey', state.creds.accessKey, ''),
		field('Secret key', 'secretKey', state.creds.secretKey, '', 'password'),
		field('Encryption key', 'encryptionKey', state.creds.encryptionKey, '', 'password'),
	);
	const actions = document.createElement('div');
	actions.className = 'actions';
	const save = document.createElement('button');
	save.className = 'primary';
	save.type = 'submit';
	save.textContent = 'save & pull';
	const fileBtn = button('open file', () => fileInput(), 'ghost');
	const forget = button('forget', () => {
		localStorage.removeItem(CREDS_KEY);
		localStorage.removeItem(SNAP_KEY);
		state.creds = loadCreds();
		state.doc = null;
		state.dirty = false;
		state.quotes = {};
		setStatus('Cleared credentials and cache on this device.');
	}, 'ghost danger');
	actions.append(save, fileBtn, forget);
	form.append(actions);
	const note = document.createElement('p');
	note.className = 'footer-note';
	note.innerHTML = `Same fields as Settings → backup on the phone. Object key is always <code>builder-launcher/backup.enc</code>. Snapshot, not two-way sync. Tasks, notes, stocks, and podcasts push together. Live Yahoo quotes need Yahoo to allow this origin; otherwise last backup prices still show. <a href="${DOCS}">Manual</a>. Built by <a href="https://cdr.xyz">Cedar Labs</a>.`;
	form.append(note);
	return form;
}

function showRow(show) {
	const row = document.createElement('button');
	row.className = 'row';
	row.type = 'button';
	const body = document.createElement('span');
	body.className = 'body';
	body.textContent = show.title;
	const meta = document.createElement('span');
	meta.className = 'meta';
	meta.textContent = show.author || '';
	row.append(body, meta);
	row.addEventListener('click', () => {
		state.tab = 'show';
		state.showId = show.feedUrl;
		render();
	});
	row.addEventListener('contextmenu', (event) => {
		event.preventDefault();
		const bag = podcastsOf(state.doc);
		mutate({ podcasts: { ...bag, ...unsubscribe(bag.shows, bag.episodes, bag.progress, show.feedUrl) } });
	});
	return row;
}

function episodeRow(episode, show, progress) {
	const row = document.createElement('button');
	const dim = finished(progress) || skipped(progress);
	row.className = `row${dim ? ' done' : ''}`;
	row.type = 'button';
	const body = document.createElement('span');
	body.className = 'body';
	body.textContent = episode.title;
	const meta = document.createElement('span');
	meta.className = 'meta';
	meta.textContent = [show?.title, formatDuration(progress?.positionMs || episode.durationMs)]
		.filter(Boolean)
		.join(' · ');
	row.append(body, meta);
	row.addEventListener('click', () => {
		state.tab = 'episode';
		state.showId = episode.showId;
		state.episodeId = episode.id;
		render();
	});
	return row;
}

function podcastHitRow(hit) {
	const row = document.createElement('button');
	row.className = 'row';
	row.type = 'button';
	const body = document.createElement('span');
	body.className = 'body';
	body.textContent = hit.title;
	const meta = document.createElement('span');
	meta.className = 'meta';
	meta.textContent = hit.author || 'subscribe';
	row.append(body, meta);
	row.addEventListener('click', () => subscribeHit(hit));
	return row;
}

function field(labelText, name, value, placeholder, type = 'text') {
	const label = document.createElement('label');
	const span = document.createElement('span');
	span.textContent = labelText;
	const input = document.createElement('input');
	input.name = name;
	input.type = type;
	input.value = value || '';
	input.placeholder = placeholder || '';
	input.autocomplete = 'off';
	input.autocapitalize = 'off';
	input.spellcheck = false;
	label.append(span, input);
	return label;
}

function composer(prefix, placeholder, onSubmit) {
	const form = document.createElement('form');
	form.className = 'composer';
	const input = document.createElement('input');
	input.placeholder = prefix ? `${prefix}${placeholder}` : placeholder;
	input.autocomplete = 'off';
	input.autocapitalize = 'off';
	input.spellcheck = false;
	const go = document.createElement('button');
	go.className = 'primary';
	go.type = 'submit';
	go.textContent = prefix || '>';
	form.append(input, go);
	form.addEventListener('submit', (event) => {
		event.preventDefault();
		const raw = input.value.trim();
		if (!raw) return;
		const text = prefix && raw.startsWith(prefix) ? raw.slice(prefix.length).trim() : raw;
		if (!text) return;
		onSubmit(text);
		input.value = '';
	});
	return form;
}

function button(label, onClick, className) {
	const el = document.createElement('button');
	el.type = 'button';
	el.className = className;
	el.textContent = label;
	el.addEventListener('click', onClick);
	return el;
}

function empty(text) {
	const p = document.createElement('p');
	p.className = 'empty';
	p.textContent = text;
	return p;
}

function section(text) {
	const h = document.createElement('p');
	h.className = 'hint';
	h.style.marginTop = '1.25rem';
	h.textContent = text;
	return h;
}

function addItem(kind, text) {
	const item = {
		id: newId(),
		kind,
		text,
		createdAt: Date.now(),
		completedAt: null,
		updatedAt: 0,
	};
	setItems([item, ...items()]);
}

function addNote(text) {
	addItem('note', seedNote(text));
	const created = items()[0];
	state.tab = 'note';
	state.noteId = created.id;
	state.noteMode = 'edit';
	render();
}

function updateNote(id, text) {
	const now = Date.now();
	if (!state.doc) return;
	state.doc = {
		...state.doc,
		items: items().map((item) => (item.id === id ? { ...item, text, updatedAt: now } : item)),
	};
	state.dirty = true;
	saveSnapshot(state.doc);
}

function toggleTodo(id) {
	const now = Date.now();
	setItems(
		items().map((item) => {
			if (item.id !== id) return item;
			return { ...item, completedAt: item.completedAt == null ? now : null };
		}),
	);
}

function removeItem(id) {
	setItems(items().filter((item) => item.id !== id));
}

function addStock(hit) {
	mutate({
		watchlist: addTicker(watchlist(state.doc), {
			symbol: hit.symbol,
			name: hit.name,
			exchange: hit.exchange || '',
			currency: 'USD',
		}),
	});
	state.hits = [];
	refreshQuotes();
}

async function searchOrAddStock(query) {
	const q = query.trim();
	if (!q) return;
	try {
		const url = `${YAHOO_SEARCH}?q=${encodeURIComponent(q)}&quotesCount=8&newsCount=0&listsCount=0`;
		const raw = await (await fetch(url)).text();
		const hits = parseYahooSearch(raw);
		if (hits.length === 1 || hits.some((hit) => hit.symbol === q.toUpperCase())) {
			addStock(hits.find((hit) => hit.symbol === q.toUpperCase()) || hits[0]);
			return;
		}
		if (hits.length) {
			state.hits = hits;
			render();
			return;
		}
	} catch {
		/* Yahoo often blocks browser CORS; still allow a typed symbol. */
	}
	if (looksLikeSymbol(q)) {
		addStock({ symbol: q.toUpperCase(), name: q.toUpperCase(), exchange: '' });
		return;
	}
	setStatus('Yahoo search blocked in this browser. Type a ticker like AAPL.');
}

async function refreshQuotes() {
	const list = watchlist(state.doc);
	if (!list.length) return;
	const next = { ...state.quotes };
	await Promise.all(
		list.slice(0, 40).map(async (item) => {
			try {
				const url = `${YAHOO_CHART}/${encodeURIComponent(item.symbol)}?interval=1d&range=1d`;
				const quote = parseYahooChart(await (await fetch(url)).text());
				if (quote) next[quote.symbol] = quote;
			} catch {
				/* keep backup price */
			}
		}),
	);
	state.quotes = next;
	if (state.tab === 'stocks') render();
}

async function searchOrSubscribe(query) {
	const q = query.trim();
	if (!q) return;
	if (q.includes('<opml') || q.includes('<outline')) {
		const hits = parseOpml(q);
		for (const hit of hits) await subscribeHit(hit, false);
		render();
		return;
	}
	if (looksLikeFeedUrl(q)) {
		await subscribeHit({ feedUrl: q, title: q, author: '', artworkUrl: '' });
		return;
	}
	try {
		const url = `${ITUNES}?term=${encodeURIComponent(q)}&media=podcast&entity=podcast&limit=8`;
		const hits = parseItunes(await (await fetch(url)).text());
		if (!hits.length) {
			setStatus('No podcast matches.');
			return;
		}
		state.hits = hits;
		render();
	} catch {
		setStatus('Podcast search blocked in this browser. Paste an RSS URL instead.');
	}
}

async function subscribeHit(hit, rerender = true) {
	ensureDoc();
	const bag = podcastsOf(state.doc);
	let feed;
	try {
		const xml = await (await fetch(hit.feedUrl)).text();
		feed = parseRss(xml, hit.feedUrl);
	} catch {
		feed = null;
	}
	if (!feed) {
		feed = {
			show: {
				feedUrl: hit.feedUrl,
				title: hit.title,
				author: hit.author || '',
				artworkUrl: hit.artworkUrl || '',
				subscribedAt: Date.now(),
				episodeOrder: 'NEWEST',
			},
			episodes: [],
		};
	}
	const merged = mergeFeed(bag.shows, bag.episodes, feed);
	mutate({ podcasts: { ...bag, shows: merged.shows, episodes: merged.episodes } });
	state.hits = [];
	state.tab = 'show';
	state.showId = hit.feedUrl;
	if (rerender) render();
}

function playingId() {
	if (!audio.src || audio.paused) return null;
	return state.episodeId;
}

function togglePlay(episode) {
	if (audio.src !== episode.enclosureUrl) {
		audio.src = episode.enclosureUrl;
	}
	if (audio.paused) audio.play().catch((err) => setStatus(err.message || 'Playback failed'));
	else audio.pause();
	render();
}

function checkpoint(force, ended = false) {
	if (!state.episodeId || !state.doc) return;
	const pos = Math.floor(audio.currentTime * 1000);
	const dur = Math.floor((audio.duration || 0) * 1000);
	const now = Date.now();
	if (!force && now - lastSavedAt < 30_000 && Math.abs(pos - lastSavedPos) < 30_000) return;
	lastSavedAt = now;
	lastSavedPos = pos;
	const bag = podcastsOf(state.doc);
	const prev = progressMap(bag.progress).get(state.episodeId);
	const next = {
		episodeId: state.episodeId,
		positionMs: pos,
		durationMs: dur || prev?.durationMs || 0,
		lastPlayedAt: now,
		finished: ended || finished({ ...prev, positionMs: pos, durationMs: dur || prev?.durationMs || 0 }),
		skipped: false,
	};
	state.doc = { ...state.doc, podcasts: { ...bag, progress: upsertProgress(bag.progress, next) } };
	state.dirty = true;
	saveSnapshot(state.doc);
	const el = document.getElementById('play-pos');
	if (el) el.textContent = `${formatDuration(pos)} / ${formatDuration(next.durationMs)}`;
}

function fileInput() {
	const input = document.createElement('input');
	input.type = 'file';
	input.accept = '.enc,.json,application/json,application/octet-stream';
	input.addEventListener('change', async () => {
		const file = input.files?.[0];
		if (!file) return;
		try {
			const buf = new Uint8Array(await file.arrayBuffer());
			if (file.name.endsWith('.json') || decodeUtf8(buf.subarray(0, 1)) === '{') {
				state.doc = JSON.parse(decodeUtf8(buf));
			} else {
				if (!state.creds.encryptionKey) throw new Error('Set encryption key first, then open the .enc file.');
				state.doc = JSON.parse(decodeUtf8(await decrypt(buf, state.creds.encryptionKey)));
			}
			state.dirty = false;
			saveSnapshot(state.doc);
			state.tab = 'tasks';
			setStatus(pulledLabel(state.doc));
			refreshQuotes();
		} catch (err) {
			setStatus(err.message || String(err));
		}
	});
	input.click();
}

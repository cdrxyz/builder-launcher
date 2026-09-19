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
	DEFAULT_PROMPT,
	PREFIXES,
	builtinPage,
	homePreview,
	isModePrompt,
	pagePrompt,
	slashMatches,
	slashResolve,
	typeMode,
} from './commands.js';
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
	tab: 'home',
	prompt: DEFAULT_PROMPT,
	draft: '',
	menu: null,
	tickerAt: 0,
	noteId: null,
	noteMode: 'edit',
	showId: null,
	episodeId: null,
	status: '',
	busy: false,
	dirty: false,
	api: false,
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

boot();

async function boot() {
	state.api = await probeApi();
	globalThis.__BL_PROXY = state.api;
	if (!readyCreds(state.creds) && !state.doc) {
		state.tab = 'settings';
		state.prompt = '/';
	}
	setInterval(() => {
		state.tickerAt += 1;
		if (state.tab === 'home' && !document.activeElement?.classList?.contains('command-input')) render();
	}, 5000);
	render();
	if (readyCreds(state.creds) && !state.doc) pull().catch(() => {});
	else if (state.doc) refreshQuotes();
}

async function probeApi() {
	try {
		const res = await fetch('/api/up', { cache: 'no-store' });
		if (!res.ok) return false;
		const body = await res.json();
		return body?.ok === true;
	} catch {
		return false;
	}
}

async function fetchText(url) {
	const res = await fetch(url);
	if (!res.ok) throw new Error(`HTTP ${res.status}`);
	return res.text();
}

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
	const focus = document.activeElement?.classList?.contains('command-input');
	const draft = state.draft;
	app.replaceChildren();
	app.append(header(), statusLine(), main(), commandDock());
	if (focus) {
		const input = app.querySelector('.command-input');
		if (input) {
			input.focus();
			input.value = draft;
		}
	}
}

function header() {
	const el = document.createElement('header');
	el.className = 'app-bar';
	if (state.tab === 'home') {
		el.append(button(state.busy ? '…' : 'pull', () => pull(), 'ghost'));
		el.append(title(''));
		el.append(button(state.dirty ? 'push*' : 'push', () => push(), 'ghost'));
		return el;
	}
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
	el.append(button('<', () => go('home'), 'ghost'));
	el.append(title(state.tab === 'pods' ? 'podcasts' : state.tab === 'tasks' ? 'tasks' : state.tab));
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
	else if (state.tab === 'tasks') el.append(tasksScreen());
	else if (state.tab === 'help') el.append(helpScreen());
	else el.append(homeScreen());
	return el;
}

function go(tab, prompt) {
	state.tab = tab;
	state.hits = [];
	state.menu = null;
	state.draft = '';
	state.prompt = prompt || pagePrompt(tab);
	if (tab !== 'note') state.noteId = null;
	if (tab !== 'show' && tab !== 'episode') state.showId = tab === 'pods' ? null : state.showId;
	if (tab !== 'episode') state.episodeId = tab === 'show' ? state.episodeId : null;
	render();
}

function homeScreen() {
	const wrap = document.createElement('div');
	wrap.className = 'home';
	const hero = document.createElement('div');
	hero.className = 'home-hero';
	const left = document.createElement('div');
	left.className = 'home-side';
	const now = playingId();
	left.textContent = now ? 'pause' : '';
	left.addEventListener('click', () => {
		if (now) go('pods');
	});
	const clock = analogClock();
	const right = document.createElement('div');
	right.className = 'home-side right';
	const list = watchlist(state.doc);
	if (list.length) {
		const item = list[state.tickerAt % list.length];
		const live = state.quotes[String(item.symbol).toUpperCase()];
		const pct = live?.changePercent ?? item.changePercent;
		const name = document.createElement('div');
		name.textContent = item.symbol;
		const change = document.createElement('div');
		change.className = `pct${pct == null ? '' : pct >= 0 ? ' up' : ' down'}`;
		change.textContent = formatPercent(pct);
		right.append(name, change);
		right.addEventListener('click', () => go('stocks'));
	}
	hero.append(left, clock, right);
	wrap.append(hero);
	if (!state.menu) {
		const todos = document.createElement('div');
		todos.className = 'home-todos';
		for (const item of homePreview(items())) todos.append(todoRow(item, false));
		wrap.append(todos);
		const more = button('… more tasks >', () => go('tasks'), 'more-link');
		wrap.append(more);
	}
	return wrap;
}

function analogClock() {
	const now = new Date();
	const wrap = document.createElement('div');
	wrap.className = 'clock';
	wrap.addEventListener('click', () => go('help'));
	const h = now.getHours() % 12;
	const m = now.getMinutes();
	const s = now.getSeconds();
	const hour = (h + m / 60) * 30;
	const minute = (m + s / 60) * 6;
	const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
	svg.setAttribute('class', 'clock-face');
	svg.setAttribute('viewBox', '0 0 100 100');
	svg.innerHTML = `
		<circle cx="50" cy="50" r="46" fill="none" stroke="#2a2a2a" stroke-width="2"/>
		<circle cx="50" cy="50" r="2.5" fill="#b7c9a8"/>
		<line x1="50" y1="50" x2="50" y2="28" stroke="#e8e4d9" stroke-width="3" stroke-linecap="round" transform="rotate(${hour} 50 50)"/>
		<line x1="50" y1="50" x2="50" y2="18" stroke="#b7c9a8" stroke-width="2" stroke-linecap="round" transform="rotate(${minute} 50 50)"/>
	`;
	const time = document.createElement('div');
	time.className = 'clock-time';
	time.textContent = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
	const date = document.createElement('div');
	date.className = 'clock-date';
	date.textContent = now.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
	wrap.append(svg, time, date);
	return wrap;
}

function helpScreen() {
	const wrap = document.createElement('div');
	wrap.className = 'help';
	const pre = document.createElement('pre');
	pre.textContent = [
		'>  type, then Enter',
		'-  todo',
		'+  note',
		'$  stock',
		'/  slash (notes stocks podcasts settings tasks pull push)',
		'',
		'Tap the prompt glyph for the prefix menu.',
		'Back on home resets to >.',
	].join('\n');
	wrap.append(pre);
	return wrap;
}

function commandDock() {
	const dock = document.createElement('div');
	dock.className = 'command-dock';
	if (state.menu === 'prefix') dock.append(prefixMenu());
	else if (state.prompt === '/' || state.menu === 'slash') dock.append(slashMenu());
	dock.append(commandBar());
	return dock;
}

function prefixMenu() {
	const list = document.createElement('div');
	list.className = 'command-list';
	for (const row of PREFIXES) {
		const btn = document.createElement('button');
		btn.type = 'button';
		btn.className = 'row';
		const mark = document.createElement('span');
		mark.className = 'mark';
		mark.textContent = row.glyph;
		const body = document.createElement('span');
		body.className = 'body';
		body.textContent = row.label;
		btn.append(mark, body);
		btn.addEventListener('click', () => pickPrefix(row.glyph));
		list.append(btn);
	}
	return list;
}

function slashMenu() {
	const list = document.createElement('div');
	list.className = 'command-list';
	for (const row of slashMatches(state.draft)) {
		const btn = document.createElement('button');
		btn.type = 'button';
		btn.className = 'row';
		const mark = document.createElement('span');
		mark.className = 'mark';
		mark.textContent = '/';
		const body = document.createElement('span');
		body.className = 'body';
		body.textContent = `${row.name}  ${row.label}`;
		btn.append(mark, body);
		btn.addEventListener('click', () => runSlash(row.name));
		list.append(btn);
	}
	return list;
}

function commandBar() {
	const form = document.createElement('form');
	form.className = 'command-bar';
	const glyph = document.createElement('button');
	glyph.type = 'button';
	glyph.className = 'prompt-glyph';
	glyph.textContent = state.prompt;
	glyph.addEventListener('click', () => {
		if (state.tab === 'home' && isModePrompt(state.prompt) && state.prompt !== DEFAULT_PROMPT && !state.draft) {
			state.prompt = DEFAULT_PROMPT;
			state.menu = null;
			render();
			return;
		}
		state.menu = state.menu === 'prefix' ? null : 'prefix';
		render();
	});
	const input = document.createElement('input');
	input.className = 'command-input';
	input.value = state.draft;
	input.autocomplete = 'off';
	input.autocapitalize = state.prompt === '$' ? 'off' : 'sentences';
	input.spellcheck = state.prompt === '-' || state.prompt === '+';
	input.addEventListener('input', () => {
		const next = typeMode(state.prompt, input.value);
		const promptChanged = next.prompt !== state.prompt;
		state.prompt = next.prompt;
		state.draft = next.input;
		if (next.prompt === '/') state.menu = 'slash';
		else if (state.menu === 'slash') state.menu = null;
		if (promptChanged || next.prompt === '/') render();
	});
	form.append(glyph, input);
	form.addEventListener('submit', (event) => {
		event.preventDefault();
		submitCommand();
	});
	return form;
}

function pickPrefix(glyph) {
	state.prompt = glyph;
	state.draft = '';
	state.menu = glyph === '/' ? 'slash' : null;
	render();
}

function submitCommand() {
	const text = state.draft.trim();
	const prompt = state.prompt;
	if (prompt === '-') {
		if (!text) return;
		addItem('todo', text);
		state.draft = '';
		if (state.tab === 'home') render();
		return;
	}
	if (prompt === '+') {
		if (!text) {
			go('notes', '+');
			return;
		}
		addNote(text);
		state.draft = '';
		return;
	}
	if (prompt === '$') {
		if (!text) {
			go('stocks', '$');
			return;
		}
		searchOrAddStock(text);
		state.draft = '';
		return;
	}
	if (prompt === '/') {
		runSlash(text);
		return;
	}
	if (prompt === '?' || prompt === '@' || prompt === '#' || prompt === '*') {
		setStatus('Use the Android app for text, calls, calendar, and AI.');
		return;
	}
	if (!text) return;
	const page = builtinPage(text);
	if (page === 'pull') {
		state.draft = '';
		pull();
		return;
	}
	if (page === 'push') {
		state.draft = '';
		push();
		return;
	}
	if (page === 'help') {
		go('help');
		return;
	}
	if (page) {
		go(page);
		return;
	}
	if (state.tab === 'pods') {
		searchOrSubscribe(text);
		state.draft = '';
		return;
	}
	setStatus('Unknown command. Tap > or type /help.');
}

function runSlash(name) {
	const hit = slashResolve(name) || builtinPage(name);
	const key = hit?.name || hit;
	state.draft = '';
	state.menu = null;
	if (key === 'pull') {
		state.prompt = DEFAULT_PROMPT;
		pull();
		return;
	}
	if (key === 'push') {
		state.prompt = DEFAULT_PROMPT;
		push();
		return;
	}
	if (key === 'help') {
		go('help');
		return;
	}
	if (key === 'home') {
		go('home');
		return;
	}
	if (key === 'notes') {
		go('notes');
		return;
	}
	if (key === 'podcasts') {
		go('pods');
		return;
	}
	if (key === 'settings') {
		go('settings');
		return;
	}
	if (key === 'stocks') {
		go('stocks');
		return;
	}
	if (key === 'tasks') {
		go('tasks');
		return;
	}
	setStatus('Unknown slash command.');
}
function tasksScreen() {
	const wrap = document.createElement('div');
	const open = openTodos(items());
	const done = doneTodos(items());
	if (!open.length && !done.length) wrap.append(empty('No tasks yet. Pull from S3 or type -buy milk.'));
	for (const item of open) wrap.append(todoRow(item, false));
	if (done.length) {
		wrap.append(section('done'));
		for (const item of done) wrap.append(todoRow(item, true));
	}
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
	if (!notes.length) wrap.append(empty('No notes yet. Pull from S3 or type +.'));
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
	note.innerHTML = `Same fields as Settings → backup on the phone. Object key is always <code>builder-launcher/backup.enc</code>. Snapshot, not two-way sync. Hosted at <a href="https://builder.cdr.xyz">builder.cdr.xyz</a> so the bucket does not need CORS. Tasks, notes, stocks, and podcasts push together. <a href="${DOCS}">Manual</a>. Built by <a href="https://cdr.xyz">Cedar Labs</a>.`;
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
		const url = state.api
			? `/api/yahoo/search?q=${encodeURIComponent(q)}`
			: `${YAHOO_SEARCH}?q=${encodeURIComponent(q)}&quotesCount=8&newsCount=0&listsCount=0`;
		const raw = await fetchText(url);
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
				const url = state.api
					? `/api/yahoo/chart?symbol=${encodeURIComponent(item.symbol)}`
					: `${YAHOO_CHART}/${encodeURIComponent(item.symbol)}?interval=1d&range=1d`;
				const quote = parseYahooChart(await fetchText(url));
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
		const url = state.api
			? `/api/podcasts/search?q=${encodeURIComponent(q)}`
			: `${ITUNES}?term=${encodeURIComponent(q)}&media=podcast&entity=podcast&limit=8`;
		const hits = parseItunes(await fetchText(url));
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
		const xml = await fetchText(
			state.api ? `/api/feed?url=${encodeURIComponent(hit.feedUrl)}` : hit.feedUrl,
		);
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
			state.tab = 'home';
			setStatus(pulledLabel(state.doc));
			refreshQuotes();
		} catch (err) {
			setStatus(err.message || String(err));
		}
	});
	input.click();
}

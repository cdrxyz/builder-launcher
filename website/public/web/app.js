import { decrypt, encrypt, decodeUtf8, encodeUtf8 } from './crypto.js';
import { credentialsReady, ready, s3Get, s3Put } from './s3.js';
import {
	apiJson,
	applyPasswordManagerAttrs,
	confirmOverwriteLocal,
	fillAccountForm,
	requestAccountCredential,
	storeAccountCredential,
} from './account.js';
import { emptyDoc, joinDocs, mergeDocs, slimDoc, isQuotaError, writeSnapshot } from './merge.js';
import {
	TASK_COMPLETE_FADE_MS,
	TASK_COMPLETE_HOLD_MS,
	displayDoneTodos,
	displayOpenTodos,
	newId,
	noteTitle,
	notesByEdited,
	openTodos,
	seedNote,
	moveOpenItems,
} from './items.js';
import { renderMarkdown } from './markdown.js';
import {
	DEFAULT_PROMPT,
	webPrefixes,
	builtinPage,
	isModePrompt,
	pagePrompt,
	slashMatches,
	slashResolve,
	typeMode,
} from './commands.js';
import {
	addTicker,
	avgVolume,
	beta,
	cagrStats,
	finished,
	formatDuration,
	formatPercent,
	formatPosition,
	formatPrice,
	formatSpeed,
	formatChange,
	formatChartTime,
	formatExtended,
	homeRows,
	applyCheckedFeeds,
	homePodcastMark,
	HOME_MARK_IDLE_MS,
	indexAt,
	looksLikeFeedUrl,
	looksLikeSymbol,
	MARKET_SYMBOL,
	mergeFeed,
	parseItunes,
	parseOpml,
	parseRss,
	parseTimeseries,
	parseYahooChart,
	parseYahooSearch,
	performance,
	FETCH_TIMEOUT_MS,
	FEED_HYDRATE_LIMIT,
	FEED_STALE_MS,
	podcastsOf,
	progressMap,
	showsNeedingFeed,
	quoteStats,
	removeTicker,
	scrubBaseline,
	skipped,
	SPEED_STEPS,
	STOCK_RANGES,
	timestamps,
	unsubscribe,
	upsertProgress,
	watchlist,
} from './media.js';
import {
	GEOCODE,
	airUrl,
	aqiLabel,
	cloud,
	daySummary,
	forecastUrl,
	homeTemperature,
	hourLabel,
	humidity,
	kindOfCode,
	parseAqi,
	parseForecast,
	parseGeocode,
	podcastMarkSvg,
	precipChance,
	pressure,
	temp,
	todayIso,
	uvLabel,
	visibility,
	weatherGlyphSvg,
	weatherLabel,
	weekday,
	wind,
} from './weather.js';

const CREDS_KEY = 'builder-launcher-web-creds';
const SNAP_KEY = 'builder-launcher-web-snapshot';
const ACCOUNT_KEY = 'builder-launcher-web-account';
const INCLUDE_AI_KEY = 'builder-launcher-web-include-ai';
const AUTOSYNC_KEY = 'builder-launcher-web-autosync';
const AUTOSYNC = [
	{ id: 'off', label: 'off' },
	{ id: 'save', label: 'on save' },
	{ id: '30s', label: '30s', ms: 30_000 },
	{ id: '5m', label: '5 min', ms: 5 * 60_000 },
];
const DOCS = 'https://cdrxyz.github.io/builder-launcher/configure/web/';
const YAHOO_CHART = 'https://query1.finance.yahoo.com/v8/finance/chart';
const YAHOO_SEARCH = 'https://query1.finance.yahoo.com/v1/finance/search';
const YAHOO_TIMESERIES = 'https://query1.finance.yahoo.com/ws/fundamentals-timeseries/v1/finance/timeseries';
const ITUNES = 'https://itunes.apple.com/search';

const audio = new Audio();
let lastSavedAt = 0;
let lastSavedPos = 0;
let listTab = null;
let feedInFlight = false;
let feedQueued = false;
const feedFailed = new Set();
const pendingComplete = new Map();
const leavingComplete = new Set();
const enteringComplete = new Set();
const completeTimers = new Map();

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
	stockSymbol: null,
	stockRange: '1D',
	stockChart: null,
	stockDetails: null,
	stockScrub: null,
	marketPoints: null,
	speed: loadSpeed(),
	speedMenu: false,
	autoSync: loadAutoSync(),
	editTodoId: null,
	status: '',
	busy: false,
	dirty: false,
	api: false,
	hits: [],
	quotes: {},
	weather: null,
	weatherHits: [],
	weatherQuery: '',
	pausedAt: 0,
	creds: loadCreds(),
	account: loadAccount(),
	accountCredPrompted: false,
	includeAi: loadIncludeAi(),
	revision: 0,
	doc: loadSnapshot(),
};

const app = document.getElementById('app');

if ('serviceWorker' in navigator) {
	navigator.serviceWorker.register('./sw.js').catch(() => {});
}

audio.playbackRate = loadSpeed();
audio.addEventListener('timeupdate', () => {
	checkpoint(false);
	updatePlayerChrome();
});
audio.addEventListener('pause', () => {
	state.pausedAt = Date.now();
	checkpoint(true);
	if (state.tab === 'home') render();
});
audio.addEventListener('play', () => {
	state.pausedAt = 0;
	if (state.tab === 'home') render();
});
audio.addEventListener('ended', () => {
	checkpoint(true, true);
	render();
});

boot();

async function boot() {
	pinVisualViewport();
	state.api = await probeApi();
	globalThis.__BL_PROXY = state.api;
	if (state.api) {
		try {
			const me = await apiJson('/api/auth/me');
			state.account = { email: me.email };
			saveAccount(state.account);
		} catch {
			if (state.account?.email && !state.account.token) state.account = { email: '', token: '' };
		}
	}
	if (!hasSync() && !state.doc) {
		state.tab = 'settings';
		state.prompt = DEFAULT_PROMPT;
	}
	setInterval(() => {
		state.tickerAt += 1;
		if (state.tab === 'home' && !document.activeElement?.classList?.contains('command-input')) render();
	}, 5000);
	armAutoSync();
	render();
	(async () => {
		if (hasSync() && (!state.doc || signedIn())) await pull({ quiet: true });
		await hydrateMedia();
	})().catch(() => {});
}

function pinVisualViewport() {
	const root = document.documentElement;
	const apply = () => {
		const vv = window.visualViewport;
		const height = vv ? vv.height : window.innerHeight;
		const top = vv ? vv.offsetTop : 0;
		root.style.setProperty('--vv-height', `${Math.round(height)}px`);
		root.style.setProperty('--vv-top', `${Math.round(top)}px`);
		if (window.scrollX || window.scrollY) window.scrollTo(0, 0);
	};
	apply();
	window.visualViewport?.addEventListener('resize', apply);
	window.visualViewport?.addEventListener('scroll', apply);
	window.addEventListener('focusin', (event) => {
		apply();
		const tag = event.target?.tagName;
		if (tag === 'INPUT' || tag === 'TEXTAREA') window.scrollTo(0, 0);
	});
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
	const res = await fetch(url, { signal: AbortSignal.timeout(FETCH_TIMEOUT_MS) });
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

function loadAccount() {
	try {
		return { email: '', token: '', ...JSON.parse(localStorage.getItem(ACCOUNT_KEY) || '{}') };
	} catch {
		return { email: '', token: '' };
	}
}

function saveAccount(account) {
	localStorage.setItem(ACCOUNT_KEY, JSON.stringify({ email: account.email || '', token: account.token || '' }));
}

function loadIncludeAi() {
	try {
		return localStorage.getItem(INCLUDE_AI_KEY) === 'true';
	} catch {
		return false;
	}
}

function saveIncludeAi(on) {
	state.includeAi = Boolean(on);
	localStorage.setItem(INCLUDE_AI_KEY, state.includeAi ? 'true' : 'false');
}

function loadAutoSync() {
	try {
		const raw = localStorage.getItem(AUTOSYNC_KEY) || 'save';
		return AUTOSYNC.some((item) => item.id === raw) ? raw : 'save';
	} catch {
		return 'save';
	}
}

function saveAutoSync(id) {
	state.autoSync = AUTOSYNC.some((item) => item.id === id) ? id : 'save';
	localStorage.setItem(AUTOSYNC_KEY, state.autoSync);
	armAutoSync();
}

let syncTimer = 0;
let saveTimer = 0;

function armAutoSync() {
	if (syncTimer) {
		clearInterval(syncTimer);
		syncTimer = 0;
	}
	const spec = AUTOSYNC.find((item) => item.id === state.autoSync);
	const ms = spec?.ms || (state.autoSync === 'save' ? 5 * 60_000 : 0);
	if (!ms) return;
	syncTimer = setInterval(() => tickAutoSync(), ms);
}

function tickAutoSync() {
	if (!hasSync()) return;
	if (state.dirty) push({ quiet: true, skipHydrate: true, skipConfirm: true }).catch(() => {});
	else if (hasSync()) pull({ quiet: true, skipHydrate: true }).catch(() => {});
}

function maybeSyncSave() {
	if (state.autoSync !== 'save' || !hasSync() || !state.dirty) return;
	if (saveTimer) clearTimeout(saveTimer);
	saveTimer = setTimeout(() => {
		saveTimer = 0;
		if (state.autoSync === 'save' && hasSync() && state.dirty) {
			push({ quiet: true, skipHydrate: true, skipConfirm: true }).catch(() => {});
		}
	}, 800);
}

function saveCreds(creds) {
	localStorage.setItem(CREDS_KEY, JSON.stringify(creds));
}

function signedIn() {
	return Boolean(state.account?.email);
}

function hasSync() {
	return signedIn() || readyCreds(state.creds);
}

function loadSnapshot() {
	try {
		return JSON.parse(localStorage.getItem(SNAP_KEY) || 'null');
	} catch {
		return null;
	}
}

function saveSnapshot(doc) {
	return writeSnapshot(localStorage, SNAP_KEY, doc);
}

function statusFromError(err) {
	if (isQuotaError(err)) return 'This browser is out of storage for the snapshot. Hard-refresh, then sync again.';
	return err?.message || String(err);
}

function readyCreds(creds) {
	return ready(creds.endpoint, creds.bucket, creds.accessKey, creds.secretKey, creds.encryptionKey);
}

function items() {
	return state.doc?.items || [];
}

function ensureDoc() {
	if (!state.doc) state.doc = emptyDoc();
}

function mutate(next) {
	ensureDoc();
	state.doc = { ...state.doc, ...next };
	state.dirty = true;
	saveSnapshot(state.doc);
	render();
	maybeSyncSave();
}

function setItems(next) {
	mutate({ items: next });
}

function setStatus(message) {
	state.status = message;
	render();
}

async function pull(opts = {}) {
	if (signedIn()) return pullAccount(opts);
	if (!readyCreds(state.creds)) {
		if (!opts.quiet) setStatus('Create a builder.cdr.xyz account, or set S3 fields.');
		return;
	}
	state.busy = true;
	if (!opts.quiet) setStatus('Pulling…');
	try {
		const blob = await s3Get(state.creds);
		const plain = await decrypt(blob, state.creds.encryptionKey);
		const remote = JSON.parse(decodeUtf8(plain));
		state.doc = opts.overwrite ? remote : joinDocs(state.doc, remote);
		state.dirty = false;
		state.hits = [];
		const saved = saveSnapshot(state.doc);
		state.status = saved === false ? statusFromError({ name: 'QuotaExceededError' }) : pulledLabel(state.doc);
		if (!opts.skipHydrate) hydrateMedia().catch(() => {});
	} catch (err) {
		if (!opts.quiet) state.status = statusFromError(err);
	} finally {
		state.busy = false;
		render();
	}
}

async function pullAccount(opts = {}) {
	state.busy = true;
	if (!opts.quiet) setStatus('Syncing…');
	try {
		const remote = await apiJson('/api/vault', { token: state.account.token });
		state.revision = remote.revision || 0;
		if (opts.overwrite && remote.document) state.doc = remote.document;
		else state.doc = joinDocs(state.doc, remote.document);
		const saved = saveSnapshot(state.doc);
		if (state.dirty || !remote.document) await pushAccount({ quiet: true, skipConfirm: true, skipHydrate: true });
		state.dirty = false;
		state.hits = [];
		state.status = saved === false ? statusFromError({ name: 'QuotaExceededError' }) : pulledLabel(state.doc);
		if (!opts.skipHydrate) hydrateMedia().catch(() => {});
	} catch (err) {
		if (!opts.quiet) state.status = statusFromError(err);
	} finally {
		state.busy = false;
		render();
	}
}

async function push(opts = {}) {
	if (signedIn()) return pushAccount(opts);
	if (!readyCreds(state.creds) || !state.doc) {
		if (!opts.quiet) setStatus('Create an account or pull a backup before pushing.');
		return;
	}
	if (
		!opts.skipConfirm &&
		!opts.quiet &&
		!window.confirm(
			'Upload this snapshot? The last successful upload wins. The phone will see these tasks, notes, stocks, and podcasts on restore.',
		)
	) {
		return;
	}
	state.busy = true;
	if (!opts.quiet) setStatus('Pushing…');
	try {
		const next = { ...state.doc, exportedAt: Date.now() };
		const blob = await encrypt(encodeUtf8(`${JSON.stringify(next, null, 2)}\n`), state.creds.encryptionKey);
		await s3Put(state.creds, blob);
		state.doc = next;
		state.dirty = false;
		saveSnapshot(state.doc);
		state.status = `Pushed ${new Date(next.exportedAt).toLocaleString()}`;
	} catch (err) {
		if (!opts.quiet) state.status = statusFromError(err);
	} finally {
		state.busy = false;
		render();
	}
}

async function pushAccount(opts = {}) {
	ensureDoc();
	state.busy = true;
	if (!opts.quiet) setStatus('Syncing…');
	try {
		const next = { ...state.doc, exportedAt: Date.now() };
		const remote = await apiJson('/api/vault', {
			method: 'PUT',
			token: state.account.token,
			body: { document: slimDoc(next) },
		});
		state.doc = joinDocs(next, remote.document || next);
		state.revision = remote.revision || state.revision;
		state.dirty = false;
		saveSnapshot(state.doc);
		state.status = `Synced ${new Date(state.doc.exportedAt).toLocaleString()}`;
		if (!opts.skipHydrate) hydrateMedia().catch(() => {});
	} catch (err) {
		if (!opts.quiet) state.status = statusFromError(err);
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
	const list = app.querySelector('main');
	const restore = list && listTab === state.tab ? list.scrollTop : 0;
	app.replaceChildren();
	if (state.tab === 'home') app.append(main(), commandDock());
	else app.append(header(), statusLine(), main(), commandDock());
	const next = app.querySelector('main');
	if (next) next.scrollTop = restore;
	listTab = state.tab;
	if (focus) {
		const input = app.querySelector('.command-input');
		if (input) {
			input.focus({ preventScroll: true });
			input.value = draft;
		}
	}
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
	if (state.tab === 'settings') {
		el.append(button('<', () => go('home'), 'ghost'));
		el.append(title('settings'));
		el.append(headerSlot());
		return el;
	}
	const back = state.tab === 'show'
		? () => go('pods')
		: state.tab === 'stock'
			? () => go('stocks')
			: state.tab === 'episode'
				? () => (state.showId ? go('show') : go('pods'))
				: () => go('home');
	el.append(button('<', back, 'ghost'));
	el.append(title(state.tab === 'pods' ? 'podcasts' : state.tab === 'tasks' ? 'tasks' : state.tab === 'stock' ? (state.stockSymbol || 'stock') : state.tab));
	el.append(gearButton());
	return el;
}

function headerSlot() {
	const el = document.createElement('span');
	el.className = 'app-bar-slot';
	return el;
}

function gearButton() {
	const el = document.createElement('button');
	el.type = 'button';
	el.className = 'ghost gear';
	el.setAttribute('aria-label', 'settings');
	el.innerHTML = '<svg viewBox="0 0 18 18" width="18" height="18" aria-hidden="true"><path d="M7.2 1.6h3.6l.55 2.05 1.85-.5 1.8 1.8-.5 1.85 2.05.55v3.6l-2.05.55.5 1.85-1.8 1.8-1.85-.5-.55 2.05H7.2l-.55-2.05-1.85.5-1.8-1.8.5-1.85L1.6 10.8V7.2l2.05-.55-.5-1.85 1.8-1.8 1.85.5.55-2.05z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><circle cx="9" cy="9" r="2.15" fill="none" stroke="currentColor" stroke-width="1.5"/></svg>';
	el.addEventListener('click', () => go('settings'));
	return el;
}

function title(text) {
	const h = document.createElement('h1');
	h.className = 'screen-title';
	h.textContent = text;
	return h;
}

function statusLine(className = 'status') {
	const p = document.createElement('p');
	p.className = className;
	p.textContent = state.status;
	return p;
}

function main() {
	const el = document.createElement('main');
	if (state.tab === 'home') el.classList.add('home-main');
	if (state.tab === 'settings') el.append(settingsScreen());
	else if (state.tab === 'notes') el.append(notesScreen());
	else if (state.tab === 'note') el.append(noteScreen());
	else if (state.tab === 'stocks') el.append(stocksScreen());
	else if (state.tab === 'stock') el.append(stockDetailScreen());
	else if (state.tab === 'pods') el.append(podsScreen());
	else if (state.tab === 'show') el.append(showScreen());
	else if (state.tab === 'episode') el.append(episodeScreen());
	else if (state.tab === 'tasks') el.append(tasksScreen());
	else if (state.tab === 'help') el.append(helpScreen());
	else if (state.tab === 'weather') el.append(weatherScreen());
	else el.append(homeScreen());
	return el;
}

function go(tab, prompt) {
	const changingPage = tab !== state.tab;
	state.tab = tab;
	state.hits = [];
	state.menu = null;
	state.draft = '';
	state.editTodoId = null;
	state.prompt = prompt || pagePrompt(tab);
	if (tab !== 'note') state.noteId = null;
	if (tab !== 'show' && tab !== 'episode') state.showId = tab === 'pods' ? null : state.showId;
	if (tab !== 'episode') state.episodeId = tab === 'show' ? state.episodeId : null;
	if (tab !== 'stock') {
		state.stockSymbol = tab === 'stocks' ? state.stockSymbol : null;
		state.stockScrub = null;
	}
	if (changingPage && (tab === 'pods' || tab === 'show')) {
		refreshPodcastFeeds({ rerender: true }).catch(() => {});
	}
	render();
}

function homeScreen() {
	const wrap = document.createElement('div');
	wrap.className = 'home';
	const hero = document.createElement('div');
	hero.className = 'home-hero';
	const left = document.createElement('div');
	left.className = 'home-side';
	left.append(podcastMark(), weatherMark());
	const cluster = document.createElement('div');
	cluster.className = 'home-clock';
	cluster.append(analogClock(), statusLine('clock-status'));
	const right = document.createElement('div');
	right.className = 'home-side right';
	const ticker = tickerMark();
	if (ticker) right.append(ticker);
	right.append(gearButton());
	hero.append(left, cluster, right);
	wrap.append(hero);
	if (!state.menu) {
		const todos = document.createElement('div');
		todos.className = 'home-todos';
		for (const item of displayOpenTodos(items(), pendingComplete).slice(0, 3)) {
			todos.append(todoRow(item, item.completedAt != null, false));
		}
		wrap.append(todos);
		const more = button('… more tasks >', () => go('tasks'), 'more-link');
		wrap.append(more);
	}
	return wrap;
}

function podcastMark() {
	const playing = Boolean(audio.src) && !audio.paused;
	const loaded = Boolean(state.episodeId && audio.src);
	const pausedFor = state.pausedAt ? Date.now() - state.pausedAt : null;
	const mark = homePodcastMark(playing, loaded, pausedFor, HOME_MARK_IDLE_MS);
	const btn = document.createElement('button');
	btn.type = 'button';
	btn.className = 'home-mark podcast-mark';
	btn.title = mark === 'PAUSE' ? 'pause' : mark === 'PLAY' ? 'play' : 'podcasts';
	btn.innerHTML = podcastMarkSvg(mark);
	btn.addEventListener('click', () => {
		if (mark === 'HEADPHONES') go('pods');
		else if (state.episodeId) {
			const ep = podcastsOf(state.doc).episodes.find((row) => row.id === state.episodeId);
			if (ep) togglePlay(ep);
			else go('pods');
		} else go('pods');
	});
	return btn;
}

function weatherMark() {
	const snap = state.weather?.current;
	if (!snap) return document.createElement('div');
	const units = weatherUnits();
	const btn = document.createElement('button');
	btn.type = 'button';
	btn.className = 'home-mark weather-mark';
	const kind = kindOfCode(snap.code);
	const icon = document.createElement('span');
	icon.className = 'weather-glyph';
	icon.innerHTML = weatherGlyphSvg(kind, snap.isDay);
	const temp = document.createElement('div');
	temp.textContent = homeTemperature(snap.temperatureC, units);
	btn.append(icon, temp);
	btn.title = `${homeTemperature(snap.temperatureC, units)} ${weatherLabel(snap.code)}`;
	btn.addEventListener('click', () => go('weather'));
	return btn;
}

function tickerMark() {
	const list = watchlist(state.doc);
	if (!list.length) return null;
	const item = list[state.tickerAt % list.length];
	const live = state.quotes[String(item.symbol).toUpperCase()];
	const pct = live?.changePercent ?? item.changePercent;
	const wrap = document.createElement('button');
	wrap.type = 'button';
	wrap.className = 'home-mark ticker-mark';
	const name = document.createElement('div');
	name.textContent = item.symbol;
	const change = document.createElement('div');
	change.className = `pct${pct == null ? '' : pct >= 0 ? ' up' : ' down'}`;
	change.textContent = formatPercent(pct) || '—';
	wrap.append(name, change);
	wrap.addEventListener('click', () => go('stocks'));
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
		'/  slash (notes stocks podcasts weather settings tasks pull push)',
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
	else if (state.menu === 'slash') dock.append(slashMenu());
	dock.append(commandBar());
	return dock;
}

function prefixMenu() {
	const list = document.createElement('div');
	list.className = 'command-list';
	for (const row of webPrefixes()) {
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
	input.enterKeyHint = promptEnterHint();
	input.addEventListener('input', () => {
		const next = typeMode(state.prompt, input.value);
		const promptChanged = next.prompt !== state.prompt;
		state.prompt = next.prompt;
		state.draft = next.input;
		if (next.prompt === '/') state.menu = 'slash';
		else if (state.menu === 'slash') state.menu = null;
		if (promptChanged || next.prompt === '/') render();
	});
	input.addEventListener('keydown', (event) => {
		if (event.key !== 'Enter' || event.isComposing) return;
		event.preventDefault();
		submitCommand();
	});
	const submit = document.createElement('button');
	submit.type = 'submit';
	submit.className = 'command-submit';
	submit.setAttribute('aria-label', commandSubmitKind(state.prompt) === 'check' ? 'save task' : 'run command');
	submit.append(commandSubmitIcon(commandSubmitKind(state.prompt)));
	form.append(glyph, input, submit);
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
	const live = app.querySelector('.command-input');
	if (live) {
		const next = typeMode(state.prompt, live.value);
		state.prompt = next.prompt;
		state.draft = next.input;
	}
	const text = state.draft.trim();
	const prompt = state.prompt;
	if (prompt === '-') {
		if (!text) return;
		const editing = state.editTodoId;
		state.draft = '';
		state.editTodoId = null;
		if (editing) updateTodoText(editing, text);
		else addItem('todo', text);
		return;
	}
	if (prompt === '+') {
		if (!text) {
			go('notes', '+');
			return;
		}
		state.draft = '';
		addNote(text);
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
	if (prompt === '?' || prompt === '@' || prompt === '#') {
		setStatus('Use the Android app for text, calls, and AI.');
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
	if (key === 'weather') {
		go('weather');
		return;
	}
	setStatus('Unknown slash command.');
}
function tasksScreen() {
	const wrap = document.createElement('div');
	const open = displayOpenTodos(items(), pendingComplete);
	const done = displayDoneTodos(items(), pendingComplete);
	const movable = open.filter((item) => item.completedAt == null);
	if (!open.length && !done.length) wrap.append(empty('No tasks yet. Pull from S3 or type -buy milk.'));
	for (const item of open) {
		const row = todoRow(item, item.completedAt != null, true, { settled: false });
		if (item.completedAt == null && movable.length > 1) {
			const index = movable.findIndex((rowItem) => rowItem.id === item.id);
			row.append(taskMoveButtons(item.id, index, movable.length));
			attachTaskDrag(row, item.id);
		}
		wrap.append(row);
	}
	if (done.length) {
		wrap.append(section('done'));
		for (const item of done) wrap.append(todoRow(item, true, true, { settled: true }));
	}
	return wrap;
}

function todoRow(item, done, details, opts = {}) {
	const row = document.createElement('div');
	row.className = `row${done ? ' done' : ''}`;
	row.dataset.todoId = item.id;
	if (leavingComplete.has(item.id)) row.classList.add('task-leaving');
	if (enteringComplete.has(item.id)) row.classList.add('task-enter');
	const body = document.createElement('button');
	body.type = 'button';
	body.className = 'body todo-text';
	body.textContent = item.text;
	noFocusScroll(body);
	const settled = Boolean(opts.settled);
	if (details) {
		row.append(taskCheckButton(done, () => toggleTodo(item.id)));
		if (done) {
			body.addEventListener('click', () => toggleTodo(item.id));
		} else {
			body.addEventListener('click', (event) => {
				if (suppressTodoEdit) {
					suppressTodoEdit = false;
					event.preventDefault();
					event.stopPropagation();
					return;
				}
				state.editTodoId = item.id;
				state.tab = 'tasks';
				state.prompt = '-';
				state.draft = item.text;
				state.menu = null;
				render();
				app.querySelector('.command-input')?.focus({ preventScroll: true });
			});
		}
	} else {
		body.addEventListener('click', () => toggleTodo(item.id));
	}
	row.append(body);
	if (details && settled && done) {
		row.append(iconButton('delete task', 'M5 5l8 8M13 5L5 13', () => removeItem(item.id)));
	}
	return row;
}

let suppressTodoEdit = false;

function moveTodo(id, delta) {
	const open = openTodos(items());
	const from = open.findIndex((item) => item.id === id);
	if (from < 0) return;
	const to = from + delta;
	if (to < 0 || to >= open.length) return;
	setItems(moveOpenItems(items(), from, to));
}

function taskMoveButtons(id, index, count) {
	const box = document.createElement('div');
	box.className = 'task-move';
	box.append(
		iconButton('move task up', 'M5 11.5L9 6.5l4 5', () => moveTodo(id, -1), index === 0),
		iconButton('move task down', 'M5 6.5L9 11.5l4-5', () => moveTodo(id, 1), index === count - 1),
	);
	return box;
}

function attachTaskDrag(row, id) {
	let timer = 0;
	let pointerId = null;
	let startY = 0;
	let startX = 0;
	let dragging = false;
	let origin = 0;
	let lastTo = 0;
	let rowH = 0;
	const clear = () => window.clearTimeout(timer);
	row.addEventListener('pointerdown', (event) => {
		if (event.button != null && event.button !== 0) return;
		if (event.target.closest('.task-check, .task-move')) return;
		pointerId = event.pointerId;
		startY = event.clientY;
		startX = event.clientX;
		origin = openTodos(items()).findIndex((item) => item.id === id);
		lastTo = origin;
		dragging = false;
		clear();
		timer = window.setTimeout(() => {
			if (origin < 0) return;
			dragging = true;
			suppressTodoEdit = true;
			row.classList.add('task-dragging');
			row.setPointerCapture(pointerId);
			const box = row.getBoundingClientRect();
			rowH = box.height + 6;
			const scroller = row.closest('main');
			if (scroller) scroller.style.overflow = 'hidden';
		}, 320);
	});
	row.addEventListener(
		'pointermove',
		(event) => {
			if (event.pointerId !== pointerId) return;
			const dy = event.clientY - startY;
			const dx = event.clientX - startX;
			if (!dragging) {
				if (Math.hypot(dx, dy) > 8) clear();
				return;
			}
			event.preventDefault();
			row.style.transform = `translateY(${dy}px)`;
			const count = openTodos(items()).length;
			const shift = rowH > 1 ? Math.round(dy / rowH) : 0;
			lastTo = Math.max(0, Math.min(count - 1, origin + shift));
		},
		{ passive: false },
	);
	const finish = (event) => {
		if (event.pointerId !== pointerId) return;
		clear();
		pointerId = null;
		const scroller = row.closest('main');
		if (scroller) scroller.style.overflow = '';
		if (!dragging) return;
		dragging = false;
		row.classList.remove('task-dragging');
		row.style.transform = '';
		suppressTodoEdit = true;
		window.setTimeout(() => {
			suppressTodoEdit = false;
		}, 0);
		if (lastTo !== origin) moveTodo(id, lastTo - origin);
	};
	row.addEventListener('pointerup', finish);
	row.addEventListener('pointercancel', finish);
}

function taskCheckButton(done, onClick) {
	const el = document.createElement('button');
	el.type = 'button';
	el.className = 'ghost row-icon task-check';
	el.setAttribute('aria-label', done ? 'reopen task' : 'complete task');
	const mark = done
		? '<path d="M6 9.2l2 2.2 4.2-5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>'
		: '';
	el.innerHTML = `<svg viewBox="0 0 18 18" width="18" height="18" aria-hidden="true"><path d="M4 4h10v10H4z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>${mark}</svg>`;
	noFocusScroll(el);
	el.addEventListener('click', (event) => {
		event.stopPropagation();
		onClick();
	});
	return el;
}

function noFocusScroll(el) {
	el.addEventListener('pointerdown', (event) => event.preventDefault());
}

function notesScreen() {
	const wrap = document.createElement('div');
	const notes = notesByEdited(items());
	if (!notes.length) wrap.append(empty('No notes yet. Pull from S3 or type +.'));
	for (const item of notes) {
		const when = item.updatedAt || item.createdAt;
		wrap.append(
			listRow({
				title: noteTitle(item.text),
				subtitle: when ? new Date(when).toLocaleDateString() : '',
				onClick: () => {
					state.tab = 'note';
					state.noteId = item.id;
					state.noteMode = 'view';
					render();
				},
			}),
		);
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
		wrap.append(
			listRow({
				title: item.symbol,
				subtitle: item.name || '',
				trail: stockTrail(
					formatPrice(price, live?.currency || item.currency),
					formatPercent(pct),
					pct == null ? 'meta' : pct >= 0 ? 'meta up' : 'meta down',
				),
				onClick: () => openStock(item.symbol),
				onContextMenu: () => mutate({ watchlist: removeTicker(watchlist(state.doc), item.symbol) }),
			}),
		);
	}
	for (const hit of state.hits) {
		wrap.append(
			listRow({
				title: hit.symbol,
				subtitle: hit.name,
				meta: hit.exchange || 'add',
				onClick: () => addStock(hit),
			}),
		);
	}
	return wrap;
}

function podsScreen() {
	const wrap = document.createElement('div');
	const now = nowPlayingBar();
	if (now) wrap.append(now);
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
	if (!list.length) {
		const failed = feedFailed.has(String(show.feedUrl || '').toLowerCase());
		wrap.append(empty(
			!feedInFlight && failed
				? 'Could not refresh this feed. Leave and reopen the show to retry.'
				: 'No episodes yet.',
		));
	}
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
	const showLine = document.createElement('p');
	showLine.className = 'hint';
	showLine.textContent = show?.title || '';
	const h = document.createElement('h2');
	h.textContent = episode.title;
	wrap.append(showLine, h);
	if (episode.enclosureUrl) {
		if (audio.src !== episode.enclosureUrl) {
			const p = progressMap(bag.progress).get(episode.id);
			audio.src = episode.enclosureUrl;
			audio.playbackRate = state.speed;
			if (p?.positionMs) audio.currentTime = p.positionMs / 1000;
		}
		const durMs = playerDurationMs(episode);
		const posMs = audio.currentTime * 1000;
		const posRow = document.createElement('div');
		posRow.className = 'player-meta';
		const pos = document.createElement('p');
		pos.className = 'hint';
		pos.id = 'play-pos';
		pos.textContent = formatPosition(posMs, durMs);
		const speedWrap = document.createElement('div');
		speedWrap.className = 'speed-wrap';
		speedWrap.append(button(formatSpeed(state.speed), () => {
			state.speedMenu = !state.speedMenu;
			render();
		}, 'ghost'));
		if (state.speedMenu) {
			const menu = document.createElement('div');
			menu.className = 'speed-menu';
			for (const step of SPEED_STEPS) {
				menu.append(button(formatSpeed(step), () => setSpeed(step), step === state.speed ? 'primary' : 'ghost'));
			}
			speedWrap.append(menu);
		}
		posRow.append(pos, speedWrap);
		const scrub = document.createElement('div');
		scrub.className = 'scrub';
		scrub.id = 'play-scrub';
		const fill = document.createElement('div');
		fill.className = 'scrub-fill';
		fill.id = 'play-scrub-fill';
		const knob = document.createElement('div');
		knob.className = 'scrub-knob';
		knob.id = 'play-scrub-knob';
		scrub.append(fill, knob);
		bindScrub(scrub, (t) => seekTo(t * durMs));
		setScrub(durMs ? posMs / durMs : 0, fill, knob);
		const controls = document.createElement('div');
		controls.className = 'player-controls';
		controls.append(
			button('−15', () => skipBy(-15_000), 'ghost'),
			button(audio.paused ? 'play' : 'pause', () => togglePlay(episode), 'primary'),
			button('+15', () => skipBy(15_000), 'ghost'),
		);
		wrap.append(posRow, scrub, controls);
	} else {
		wrap.append(empty('No audio URL in this episode.'));
	}
	if (episode.description) {
		const notes = document.createElement('div');
		notes.className = 'note-preview';
		const plain = episode.description.replace(/<[^>]+>/g, '');
		notes.append(notesWithTimestamps(plain));
		wrap.append(section('Show notes'), notes);
	}
	return wrap;
}

function settingsScreen() {
	const wrap = document.createElement('div');
	wrap.className = 'settings';
	wrap.append(accountCard(), autoSyncCard(), weatherCard(), includeAiCard(), s3Card());
	const note = document.createElement('p');
	note.className = 'footer-note';
	note.innerHTML = `Preferred: a <strong>builder.cdr.xyz</strong> account. Sign-in overwrites local data with the account snapshot. Sync now merges. Opt in below to include AI API keys for true ? sync. S3 is optional. <a href="${DOCS}">Manual</a>. Built by <a href="https://cdr.xyz">Cedar Labs</a>.`;
	wrap.append(note);
	return wrap;
}

function accountCard() {
	const form = document.createElement('form');
	form.className = 'settings';
	form.id = 'builder-account-form';
	form.method = 'post';
	form.action = '/api/auth/login';
	form.autocomplete = 'on';
	const heading = document.createElement('p');
	heading.className = 'hint';
	heading.textContent = signedIn() ? `Signed in as ${state.account.email}` : 'Builder account (recommended)';
	form.append(heading);
	if (!signedIn()) {
		form.append(
			field('Email', 'email', state.account.email, 'you@example.com', 'email'),
			field('Password', 'password', '', '8+ characters', 'password'),
		);
		promptAccountPassword(form);
	}
	const actions = document.createElement('div');
	actions.className = 'actions';
	if (signedIn()) {
		actions.append(
			button('sync now', () => pull(), 'primary'),
			button('sign out', () => signOut(), 'ghost'),
		);
	} else {
		const create = document.createElement('button');
		create.className = 'primary';
		create.type = 'submit';
		create.name = 'intent';
		create.value = 'signup';
		create.textContent = 'create account';
		const signIn = document.createElement('button');
		signIn.className = 'ghost';
		signIn.type = 'submit';
		signIn.name = 'intent';
		signIn.value = 'login';
		signIn.textContent = 'sign in';
		form.addEventListener('submit', (event) => {
			event.preventDefault();
			const intent = event.submitter?.value === 'signup' ? 'signup' : 'login';
			const passwordInput = form.querySelector('[name="password"]');
			if (passwordInput) applyPasswordManagerAttrs(passwordInput, 'password', 'password', intent);
			submitAccount(form, intent);
		});
		actions.append(create, signIn);
	}
	form.append(actions);
	return form;
}

function promptAccountPassword(form) {
	if (state.accountCredPrompted) return;
	state.accountCredPrompted = true;
	requestAccountCredential().then((cred) => {
		fillAccountForm(document.getElementById('builder-account-form') || form, cred);
	});
}

function autoSyncCard() {
	const wrap = document.createElement('div');
	wrap.className = 'settings';
	const heading = document.createElement('p');
	heading.className = 'hint';
	heading.textContent = 'Auto-sync';
	const actions = document.createElement('div');
	actions.className = 'actions';
	for (const item of AUTOSYNC) {
		actions.append(
			button(item.label, () => {
				saveAutoSync(item.id);
				render();
			}, state.autoSync === item.id ? 'primary' : 'ghost'),
		);
	}
	const hint = document.createElement('p');
	hint.className = 'hint';
	hint.textContent = state.autoSync === 'off'
		? 'Off. Use sync now in the account card, or /pull and /push.'
		: state.autoSync === 'save'
			? 'Pushes a few hundred milliseconds after you edit. Pulls when you open the app, every 5 minutes while it is open, or when you tap sync now.'
			: `Pushes when dirty, otherwise pulls, every ${state.autoSync === '5m' ? '5 minutes' : '30 seconds'}.`;
	wrap.append(heading, actions, hint);
	return wrap;
}

function includeAiCard() {
	const wrap = document.createElement('div');
	wrap.className = 'settings';
	const row = document.createElement('button');
	row.type = 'button';
	row.className = 'row';
	const body = document.createElement('span');
	body.className = 'body';
	body.textContent = state.includeAi ? '[x] Include AI credentials' : '[ ] Include AI credentials';
	row.append(body);
	row.addEventListener('click', () => {
		saveIncludeAi(!state.includeAi);
		render();
	});
	const hint = document.createElement('p');
	hint.className = 'hint';
	hint.textContent = state.includeAi
		? 'On. The phone includes the current provider API key in Builder account sync, S3, and JSON share so other devices can use the same ?. OAuth tokens stay on the phone. This PWA has no API keys of its own.'
		: 'Off. API keys stay out of Builder account sync, S3 backups, and the JSON share.';
	wrap.append(row, hint);
	return wrap;
}

function s3Card() {
	const details = document.createElement('details');
	details.className = 's3-secondary';
	if (readyCreds(state.creds) && !signedIn()) details.open = true;
	const summary = document.createElement('summary');
	summary.textContent = 'S3 backup (optional)';
	const form = document.createElement('form');
	form.className = 'settings';
	form.addEventListener('submit', (event) => {
		event.preventDefault();
		if (!confirmOverwriteLocal()) return;
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
				? 'Saved S3 on this device. Pull to load the snapshot.'
				: 'Need endpoint, bucket, access key, and secret key.',
		);
		if (readyCreds(state.creds) && !signedIn()) pull({ overwrite: true });
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
	const forget = button('forget S3', () => {
		localStorage.removeItem(CREDS_KEY);
		state.creds = loadCreds();
		setStatus('Cleared S3 credentials on this device.');
	}, 'ghost danger');
	actions.append(save, fileBtn, forget);
	form.append(actions);
	details.append(summary, form);
	return details;
}

// iOS enterkeyhint=done is a check that dismisses the keyboard without adding the task.
function promptEnterHint() {
	return 'go';
}

function commandSubmitKind(prompt) {
	return prompt === '-' ? 'check' : 'send';
}

function commandSubmitIcon(kind) {
	const mark = document.createElement('span');
	mark.textContent = kind === 'check' ? '✓' : '→';
	return mark;
}

function weatherSettings() {
	return state.doc?.settings || {};
}

function weatherUnits() {
	return String(weatherSettings().weatherUnits || 'METRIC').toUpperCase() === 'IMPERIAL' ? 'IMPERIAL' : 'METRIC';
}

function setWeatherSettings(next) {
	ensureDoc();
	mutate({ settings: { ...weatherSettings(), ...next } });
	refreshWeather(true);
}

function weatherCard() {
	const wrap = document.createElement('div');
	wrap.className = 'settings';
	const heading = document.createElement('p');
	heading.className = 'hint';
	heading.textContent = 'Weather';
	const loc = field('Weather location', 'weatherPlace', state.weatherQuery || weatherSettings().weatherPlace || '', 'Kitchener');
	const input = loc.querySelector('input');
	input.addEventListener('input', () => {
		const q = input.value.trim();
		state.weatherQuery = input.value;
		if (!q) {
			state.weatherHits = [];
			setWeatherSettings({ weatherPlace: '', weatherLat: null, weatherLon: null });
			return;
		}
		searchWeatherPlaces(q);
	});
	const hits = document.createElement('div');
	for (const place of state.weatherHits) {
		hits.append(
			button(place.label, () => {
				state.weatherHits = [];
				state.weatherQuery = place.label;
				setWeatherSettings({
					weatherPlace: place.label,
					weatherLat: place.latitude,
					weatherLon: place.longitude,
				});
			}, 'row'),
		);
	}
	const units = document.createElement('div');
	units.className = 'actions';
	for (const item of ['METRIC', 'IMPERIAL']) {
		units.append(
			button(item.toLowerCase(), () => setWeatherSettings({ weatherUnits: item }), weatherUnits() === item ? 'primary' : 'ghost'),
		);
	}
	const hint = document.createElement('p');
	hint.className = 'hint';
	hint.textContent = weatherSettings().weatherLat
		? `${weatherSettings().weatherPlace}. Tap the home mark for the forecast. Units ${weatherUnits().toLowerCase()}.`
		: 'Type a city and pick a match. No GPS. Open-Meteo.';
	wrap.append(heading, loc, hits, units, hint);
	return wrap;
}

function weatherScreen() {
	const wrap = document.createElement('div');
	wrap.className = 'weather';
	const snap = state.weather;
	const place = String(weatherSettings().weatherPlace || '').trim();
	if (!weatherSettings().weatherLat) {
		wrap.append(empty('Set a city in settings.'));
		wrap.append(button('settings', () => go('settings'), 'linkish'));
		return wrap;
	}
	if (!snap?.current) {
		wrap.append(empty('Waiting on forecast…'));
		refreshWeather();
		return wrap;
	}
	const units = weatherUnits();
	const now = snap.current;
	const today = (snap.daily || [])[0];
	const placeEl = document.createElement('p');
	placeEl.className = 'weather-place';
	placeEl.textContent = place || 'Here';
	const hero = document.createElement('div');
	hero.className = 'weather-hero';
	const tempEl = document.createElement('div');
	tempEl.className = 'weather-temp';
	tempEl.textContent = String(temp(now.temperatureC, units)).replace(/°$/, '');
	const glyph = document.createElement('span');
	glyph.className = 'weather-glyph hero';
	glyph.innerHTML = weatherGlyphSvg(kindOfCode(now.code), now.isDay);
	glyph.setAttribute('aria-label', weatherLabel(now.code));
	hero.append(tempEl, glyph);
	const cond = document.createElement('p');
	cond.className = 'weather-cond';
	cond.textContent = weatherLabel(now.code);
	wrap.append(placeEl, hero, cond);
	if (today) {
		const hl = document.createElement('p');
		hl.className = 'weather-hl';
		hl.textContent = `H ${temp(today.highC, units)}  L ${temp(today.lowC, units)}`;
		wrap.append(hl);
	}
	const stats = document.createElement('div');
	stats.className = 'weather-stats';
	stats.append(
		weatherStat('Feels', temp(now.feelsC, units)),
		weatherStat('Precip', precipChance(now.precipProb)),
		weatherStat('Wind', wind(now.windKmh, now.windDir, units)),
	);
	wrap.append(stats);
	const hours = document.createElement('div');
	hours.className = 'weather-hours';
	for (const hour of snap.hourly || []) {
		const col = document.createElement('div');
		col.className = 'weather-hour';
		const when = document.createElement('span');
		when.className = 'weather-hour-label';
		when.textContent = hourLabel(hour.epochMs, snap.timezone);
		const icon = document.createElement('span');
		icon.className = 'weather-glyph sm';
		icon.innerHTML = weatherGlyphSvg(kindOfCode(hour.code), hour.isDay);
		const deg = document.createElement('span');
		deg.className = 'weather-hour-temp';
		deg.textContent = temp(hour.temperatureC, units);
		const pop = document.createElement('span');
		pop.className = 'weather-hour-pop';
		pop.textContent = precipChance(hour.precipProb);
		col.append(when, icon, deg, pop);
		hours.append(col);
	}
	wrap.append(hours);
	const days = document.createElement('div');
	days.className = 'weather-days';
	const todayKey = todayIso();
	for (const day of (snap.daily || []).slice(0, 7)) {
		const row = document.createElement('div');
		row.className = 'weather-day';
		const name = document.createElement('span');
		name.className = 'weather-day-name';
		name.textContent = weekday(day.date, todayKey);
		const icon = document.createElement('span');
		icon.className = 'weather-glyph sm';
		icon.innerHTML = weatherGlyphSvg(kindOfCode(day.code), true);
		const note = document.createElement('span');
		note.className = 'weather-day-note';
		note.textContent = daySummary(day, units);
		const hi = document.createElement('span');
		hi.className = 'weather-day-hi';
		hi.textContent = `${temp(day.highC, units)}  ${temp(day.lowC, units)}`;
		row.append(name, icon, note, hi);
		days.append(row);
	}
	wrap.append(days);
	const details = document.createElement('div');
	details.className = 'weather-details';
	const rows = [
		['Humidity', humidity(now.humidity)],
		['Dew point', now.dewC == null ? '—' : temp(now.dewC, units)],
		['UV index', uvLabel(now.uv ?? today?.uv)],
		['Pressure', pressure(now.pressureHpa, units)],
		['Visibility', visibility(now.visibilityM, units)],
		['Cloud cover', cloud(now.cloud)],
		['Sunrise', today?.sunrise || '—'],
		['Sunset', today?.sunset || '—'],
		['Wind', wind(now.windKmh, now.windDir, units)],
		['Gusts', now.gustKmh == null ? '—' : wind(now.gustKmh, null, units)],
		['Air quality', aqiLabel(snap.aqi)],
	];
	for (const [label, value] of rows) {
		const row = document.createElement('div');
		row.className = 'stat-row weather-detail';
		const left = document.createElement('span');
		left.className = 'hint';
		left.textContent = label;
		const right = document.createElement('span');
		right.textContent = value;
		row.append(left, right);
		details.append(row);
	}
	wrap.append(details);
	return wrap;
}

function weatherStat(label, value) {
	const col = document.createElement('div');
	col.className = 'weather-stat';
	const name = document.createElement('span');
	name.className = 'weather-stat-label';
	name.textContent = label.toUpperCase();
	const val = document.createElement('span');
	val.className = 'weather-stat-value';
	val.textContent = value;
	col.append(name, val);
	return col;
}

let weatherSearchAt = 0;

async function searchWeatherPlaces(query) {
	const token = ++weatherSearchAt;
	try {
		const url = state.api
			? `/api/weather/search?q=${encodeURIComponent(query)}`
			: `${GEOCODE}?name=${encodeURIComponent(query)}&count=6&language=en&format=json`;
		const hits = parseGeocode(await fetchText(url));
		if (token !== weatherSearchAt) return;
		state.weatherHits = hits;
		if (state.tab === 'settings') {
			const focus = document.activeElement?.name === 'weatherPlace';
			const draft = state.weatherQuery;
			render();
			if (focus) {
				const input = app.querySelector('input[name="weatherPlace"]');
				if (input) {
					input.focus({ preventScroll: true });
					input.value = draft;
				}
			}
		}
	} catch {
		if (token !== weatherSearchAt) return;
		state.weatherHits = [];
	}
}

async function refreshWeather(force = false) {
	const s = weatherSettings();
	const lat = Number(s.weatherLat);
	const lon = Number(s.weatherLon);
	if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
		state.weather = null;
		return;
	}
	if (!force && state.weather?.fetchedAt && Date.now() - state.weather.fetchedAt < 15 * 60_000) return;
	try {
		const qs = `lat=${encodeURIComponent(lat)}&lon=${encodeURIComponent(lon)}`;
		const url = state.api ? `/api/weather/forecast?${qs}` : forecastUrl(lat, lon);
		const air = state.api ? `/api/weather/air?${qs}` : airUrl(lat, lon);
		const [raw, aqiRaw] = await Promise.all([
			fetchText(url),
			fetchText(air).catch(() => ''),
		]);
		const next = parseForecast(raw, Date.now(), parseAqi(aqiRaw));
		if (!next) return;
		state.weather = next;
		if (state.tab === 'home' || state.tab === 'weather') render();
	} catch {
		/* keep last snapshot */
	}
}

async function submitAccount(form, mode) {
	if (!confirmOverwriteLocal()) return;
	const data = new FormData(form);
	const email = String(data.get('email') || '').trim();
	const password = String(data.get('password') || '');
	state.busy = true;
	setStatus(mode === 'signup' ? 'Creating account…' : 'Signing in…');
	try {
		const path = mode === 'signup' ? '/api/auth/signup' : '/api/auth/login';
		const res = await apiJson(path, { method: 'POST', body: { email, password } });
		await storeAccountCredential(email, password);
		state.account = { email: res.email, token: res.token || '' };
		saveAccount(state.account);
		state.status = `Signed in as ${res.email}`;
		await pull({ overwrite: mode === 'login' });
	} catch (err) {
		state.status = err.message || String(err);
		state.busy = false;
		render();
	}
}

async function signOut() {
	try {
		await apiJson('/api/auth/logout', { method: 'POST', token: state.account.token, body: {} });
	} catch {
		/* cookie may already be gone */
	}
	localStorage.removeItem(ACCOUNT_KEY);
	state.account = { email: '', token: '' };
	setStatus('Signed out. Local snapshot stays on this device.');
}

function showRow(show) {
	return listRow({
		title: show.title,
		subtitle: show.author || '',
		onClick: () => {
			state.showId = show.feedUrl;
			go('show');
		},
		onContextMenu: () => {
			const bag = podcastsOf(state.doc);
			mutate({ podcasts: { ...bag, ...unsubscribe(bag.shows, bag.episodes, bag.progress, show.feedUrl) } });
		},
	});
}

function episodeRow(episode, show, progress) {
	return listRow({
		title: episode.title,
		subtitle: show?.title || '',
		meta: formatDuration(progress?.positionMs || episode.durationMs),
		dim: finished(progress) || skipped(progress),
		onClick: () => {
			state.tab = 'episode';
			state.showId = episode.showId;
			state.episodeId = episode.id;
			render();
		},
	});
}

function podcastHitRow(hit) {
	return listRow({
		title: hit.title,
		subtitle: hit.author || 'subscribe',
		onClick: () => subscribeHit(hit),
	});
}

function listRow({ title, subtitle = '', meta = '', dim = false, trail = null, onClick, onContextMenu }) {
	const row = document.createElement('button');
	row.type = 'button';
	row.className = `row${dim ? ' done' : ''}`;
	const copy = document.createElement('span');
	copy.className = 'copy';
	const body = document.createElement('span');
	body.className = 'body';
	body.textContent = title;
	copy.append(body);
	if (subtitle) {
		const sub = document.createElement('span');
		sub.className = 'sub';
		sub.textContent = subtitle;
		copy.append(sub);
	}
	row.append(copy);
	if (trail) row.append(trail);
	else if (meta) {
		const side = document.createElement('span');
		side.className = 'meta';
		side.textContent = meta;
		row.append(side);
	}
	if (onClick) row.addEventListener('click', onClick);
	if (onContextMenu) {
		row.addEventListener('contextmenu', (event) => {
			event.preventDefault();
			onContextMenu(event);
		});
	}
	return row;
}

function stockTrail(priceText, pctText, pctClass) {
	const trail = document.createElement('span');
	trail.className = 'trail';
	if (priceText) {
		const price = document.createElement('span');
		price.className = 'price';
		price.textContent = priceText;
		trail.append(price);
	}
	if (pctText) {
		const pct = document.createElement('span');
		pct.className = pctClass;
		pct.textContent = pctText;
		trail.append(pct);
	}
	return trail;
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
	applyPasswordManagerAttrs(input, name, type);
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

function iconButton(label, path, onClick, disabled = false) {
	const el = document.createElement('button');
	el.type = 'button';
	el.className = 'ghost row-icon';
	el.setAttribute('aria-label', label);
	el.innerHTML = `<svg viewBox="0 0 18 18" width="18" height="18" aria-hidden="true"><path d="${path}" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
	if (disabled) {
		el.disabled = true;
		return el;
	}
	el.addEventListener('click', (event) => {
		event.stopPropagation();
		onClick();
	});
	return el;
}

function nowPlayingBar() {
	if (!state.episodeId) return null;
	const bag = podcastsOf(state.doc);
	const episode = bag.episodes.find((item) => item.id === state.episodeId);
	if (!episode) return null;
	const progress = progressMap(bag.progress).get(episode.id);
	if (finished(progress) && audio.paused) return null;
	const show = bag.shows.find((item) => item.feedUrl === episode.showId);
	const wrap = document.createElement('div');
	wrap.className = 'now-playing';
	const copy = document.createElement('button');
	copy.type = 'button';
	copy.className = 'copy';
	const label = document.createElement('span');
	label.className = 'label';
	label.textContent = 'now playing';
	const titleEl = document.createElement('span');
	titleEl.className = 'title';
	titleEl.textContent = episode.title;
	copy.append(label, titleEl);
	if (show?.title) {
		const sub = document.createElement('span');
		sub.className = 'sub';
		sub.textContent = show.title;
		copy.append(sub);
	}
	copy.addEventListener('click', () => {
		state.tab = 'episode';
		state.showId = episode.showId;
		state.episodeId = episode.id;
		render();
	});
	const play = document.createElement('button');
	play.type = 'button';
	play.className = 'ghost play';
	play.setAttribute('aria-label', audio.paused ? 'play' : 'pause');
	play.innerHTML = audio.paused
		? '<svg viewBox="0 0 18 18" width="18" height="18" aria-hidden="true"><path d="M5 3.5l11 5.5L5 14.5z" fill="currentColor"/></svg>'
		: '<svg viewBox="0 0 18 18" width="18" height="18" aria-hidden="true"><path d="M5 3.5h3v11H5zM10 3.5h3v11h-3z" fill="currentColor"/></svg>';
	play.addEventListener('click', () => togglePlay(episode));
	wrap.append(copy, play);
	return wrap;
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
	maybeSyncSave();
}

function updateTodoText(id, text) {
	setItems(items().map((item) => (item.id === id ? { ...item, text, updatedAt: Date.now() } : item)));
}

function clearCompleteTimer(id) {
	const timers = completeTimers.get(id);
	if (!timers) return;
	clearTimeout(timers.hold);
	clearTimeout(timers.fade);
	completeTimers.delete(id);
}

function beginCompleteAnim(id, index) {
	pendingComplete.set(id, index);
	leavingComplete.delete(id);
	enteringComplete.delete(id);
	clearCompleteTimer(id);
	const timers = {};
	timers.hold = setTimeout(() => {
		leavingComplete.add(id);
		const row = app.querySelector(`[data-todo-id="${id}"]`);
		if (row) row.classList.add('task-leaving');
		else render();
		timers.fade = setTimeout(() => {
			pendingComplete.delete(id);
			leavingComplete.delete(id);
			enteringComplete.add(id);
			completeTimers.delete(id);
			render();
			setTimeout(() => enteringComplete.delete(id), TASK_COMPLETE_FADE_MS);
		}, TASK_COMPLETE_FADE_MS);
	}, TASK_COMPLETE_HOLD_MS);
	completeTimers.set(id, timers);
}

function cancelCompleteAnim(id) {
	clearCompleteTimer(id);
	pendingComplete.delete(id);
	leavingComplete.delete(id);
	enteringComplete.delete(id);
}

function toggleTodo(id) {
	const list = items();
	const item = list.find((entry) => entry.id === id);
	if (!item) return;
	const markingDone = item.completedAt == null;
	if (markingDone) {
		const shown = displayOpenTodos(list, pendingComplete);
		const index = shown.findIndex((entry) => entry.id === id);
		beginCompleteAnim(id, index < 0 ? shown.length : index);
	} else {
		cancelCompleteAnim(id);
	}
	const now = Date.now();
	setItems(
		list.map((entry) => {
			if (entry.id !== id) return entry;
			return { ...entry, completedAt: markingDone ? now : null };
		}),
	);
}

function removeItem(id) {
	ensureDoc();
	const deletedIds = [...new Set([...(state.doc.deletedIds || []), id])];
	mutate({ items: items().filter((item) => item.id !== id), deletedIds });
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

async function hydrateMedia() {
	await Promise.all([refreshQuotes(), refreshPodcastFeeds(), refreshWeather()]);
}

function feedSource(feedUrl) {
	// Most feeds omit Access-Control-Allow-Origin, so a direct browser fetch
	// fails even from localhost. Use the Worker when it is up, and always on
	// a deployed origin. Direct fetch is only the fallback for a local static
	// preview with no Worker.
	if (state.api || !isLocalOrigin()) return `/api/feed?url=${encodeURIComponent(feedUrl)}`;
	return feedUrl;
}

function isLocalOrigin() {
	const host = location.hostname;
	return host === 'localhost' || host === '127.0.0.1' || host === '::1' || /\.local$/.test(host);
}

async function refreshPodcastFeeds(opts = {}) {
	// A navigation during a pass must not be dropped. The running pass paints
	// the screen the user is on when it finishes, then runs one more pass if
	// another refresh was requested while it was in flight.
	if (feedInFlight) {
		feedQueued = true;
		return;
	}
	feedInFlight = true;
	try {
		let pass = 0;
		do {
			feedQueued = false;
			const bag = podcastsOf(state.doc);
			const stale = showsNeedingFeed(bag, Date.now(), FEED_STALE_MS);
			if (!stale.length) break;
			await runFeedPass(stale, bag, opts);
			pass += 1;
		} while (feedQueued && pass < 3);
	} finally {
		feedInFlight = false;
		if (state.tab === 'pods' || state.tab === 'show') render();
	}
}

async function runFeedPass(stale, bag, opts) {
	const feeds = new Array(stale.length);
	let cursor = 0;
	const worker = async () => {
		while (cursor < stale.length) {
			const index = cursor;
			cursor += 1;
			const show = stale[index];
			try {
				const xml = await fetchText(feedSource(show.feedUrl));
				feeds[index] = parseRss(xml, show.feedUrl);
			} catch {
				feeds[index] = null;
			}
		}
	};
	await Promise.all(Array.from({ length: Math.min(FEED_HYDRATE_LIMIT, stale.length) }, worker));
	const checkedAt = Date.now();
	const parsed = stale.map((show, index) => ({ feedUrl: show.feedUrl, feed: feeds[index] || null }));
	for (const row of parsed) {
		const key = String(row.feedUrl || '').toLowerCase();
		if (!key) continue;
		if (row.feed) feedFailed.delete(key);
		else feedFailed.add(key);
	}
	const applied = applyCheckedFeeds(bag.shows, bag.episodes, parsed, checkedAt);
	const prev = bag.shows || [];
	const changed = applied.episodes !== bag.episodes
		|| applied.shows.length !== prev.length
		|| applied.shows.some((show, i) => show !== prev[i]);
	if (changed) {
		state.doc = { ...state.doc, podcasts: { ...bag, shows: applied.shows, episodes: applied.episodes } };
		saveSnapshot(state.doc);
	}
	// One delayed retry for shows that failed. Successes are already saved, so
	// a dead host does not hide the rest of the catalog or schedule another wave.
	if (applied.failed.length && !opts.retry) {
		setTimeout(() => refreshPodcastFeeds({ retry: true }).catch(() => {}), 60_000);
	}
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
		const hits = await searchPodcastCatalog(q);
		if (!hits.length) {
			setStatus('No podcast matches.');
			return;
		}
		state.hits = hits;
		render();
	} catch {
		setStatus('Podcast search failed. Paste an RSS URL instead.');
	}
}

async function searchPodcastCatalog(q) {
	const itunes = `${ITUNES}?term=${encodeURIComponent(q)}&media=podcast&entity=podcast&limit=8`;
	try {
		const hits = parseItunes(await fetchText(itunes));
		if (hits.length) return hits;
	} catch {
		/* Cloudflare cannot fetch itunes.apple.com; browsers can (CORS *). */
	}
	if (!state.api) return [];
	return parseItunes(await fetchText(`/api/podcasts/search?q=${encodeURIComponent(q)}`));
}

async function subscribeHit(hit, rerender = true) {
	ensureDoc();
	const bag = podcastsOf(state.doc);
	let feed;
	try {
		const xml = await fetchText(feedSource(hit.feedUrl));
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
	maybeSyncSave();
	const el = document.getElementById('play-pos');
	if (el) el.textContent = formatPosition(pos, next.durationMs);
	updatePlayerChrome();
}

function loadSpeed() {
	try {
		const n = Number(localStorage.getItem('builder-launcher-web-speed'));
		return SPEED_STEPS.includes(n) ? n : 1;
	} catch {
		return 1;
	}
}

function setSpeed(speed) {
	state.speed = snapOr(speed);
	state.speedMenu = false;
	audio.playbackRate = state.speed;
	try {
		localStorage.setItem('builder-launcher-web-speed', String(state.speed));
	} catch {
		/* ignore */
	}
	render();
}

function snapOr(speed) {
	return SPEED_STEPS.reduce((best, step) => (Math.abs(step - speed) < Math.abs(best - speed) ? step : best), 1);
}

function playerDurationMs(episode) {
	if (Number.isFinite(audio.duration) && audio.duration > 0) return audio.duration * 1000;
	return episode?.durationMs || 0;
}

function skipBy(deltaMs) {
	const cap = Number.isFinite(audio.duration) && audio.duration > 0 ? audio.duration : 0;
	audio.currentTime = Math.min(Math.max(0, audio.currentTime + deltaMs / 1000), cap || audio.currentTime + deltaMs / 1000);
	checkpoint(true);
	updatePlayerChrome();
}

function seekTo(ms) {
	audio.currentTime = Math.max(0, ms / 1000);
	checkpoint(true);
	updatePlayerChrome();
}

function setScrub(t, fill, knob) {
	const pct = `${Math.min(1, Math.max(0, t)) * 100}%`;
	if (fill) fill.style.width = pct;
	if (knob) knob.style.left = pct;
}

function updatePlayerChrome() {
	const bag = podcastsOf(state.doc);
	const episode = bag.episodes.find((item) => item.id === state.episodeId);
	const durMs = playerDurationMs(episode);
	const posMs = audio.currentTime * 1000;
	const el = document.getElementById('play-pos');
	if (el) el.textContent = formatPosition(posMs, durMs);
	setScrub(durMs ? posMs / durMs : 0, document.getElementById('play-scrub-fill'), document.getElementById('play-scrub-knob'));
}

function bindScrub(el, onFraction) {
	const read = (event) => {
		const rect = el.getBoundingClientRect();
		const x = (event.touches ? event.touches[0].clientX : event.clientX) - rect.left;
		onFraction(Math.min(1, Math.max(0, x / rect.width)));
	};
	el.addEventListener('pointerdown', (event) => {
		event.preventDefault();
		el.setPointerCapture?.(event.pointerId);
		read(event);
		const move = (ev) => read(ev);
		const up = () => {
			el.removeEventListener('pointermove', move);
			el.removeEventListener('pointerup', up);
		};
		el.addEventListener('pointermove', move);
		el.addEventListener('pointerup', up);
	});
}

function notesWithTimestamps(plain) {
	const wrap = document.createElement('p');
	const hits = timestamps(plain);
	let i = 0;
	for (const hit of hits) {
		if (hit.start > i) wrap.append(plain.slice(i, hit.start));
		const link = document.createElement('button');
		link.type = 'button';
		link.className = 'stamp';
		link.textContent = hit.raw;
		link.addEventListener('click', () => seekTo(hit.positionMs));
		wrap.append(link);
		i = hit.end;
	}
	if (i < plain.length) wrap.append(plain.slice(i));
	return wrap;
}

function openStock(symbol) {
	state.tab = 'stock';
	state.stockSymbol = String(symbol || '').toUpperCase();
	state.stockRange = '1D';
	state.stockChart = null;
	state.stockDetails = null;
	state.stockScrub = null;
	state.hits = [];
	state.prompt = '$';
	render();
	loadStockChart();
	loadStockDetails();
}

async function loadStockChart() {
	const symbol = state.stockSymbol;
	if (!symbol) return;
	const range = STOCK_RANGES.find((row) => row.label === state.stockRange) || STOCK_RANGES[0];
	try {
		const chart = await fetchYahooChart(symbol, range.range, range.interval);
		if (state.stockSymbol !== symbol) return;
		state.stockChart = chart;
		if (chart) state.quotes = { ...state.quotes, [chart.symbol]: chart };
		if (state.tab === 'stock') render();
	} catch (err) {
		if (state.tab === 'stock') setStatus(err.message || 'Chart failed');
	}
}

async function loadStockDetails() {
	const symbol = state.stockSymbol;
	if (!symbol) return;
	try {
		const [long, vol, funds, market] = await Promise.all([
			fetchYahooChart(symbol, '10y', '1wk').catch(() => null),
			fetchYahooChart(symbol, '3mo', '1d').catch(() => null),
			fetchYahooTimeseries(symbol).catch(() => ({})),
			state.marketPoints
				? Promise.resolve({ points: state.marketPoints })
				: fetchYahooChart(MARKET_SYMBOL, '10y', '1wk').catch(() => null),
		]);
		if (state.stockSymbol !== symbol) return;
		if (market?.points?.length >= 30) state.marketPoints = market.points;
		const extra = funds || {};
		const quote = {
			...(long || vol || {}),
			pe: extra.pe,
			marketCap: extra.marketCap,
			dividendYield: extra.dividendYield,
			eps: extra.eps,
			beta: beta(long?.points || [], state.marketPoints || market?.points || []),
			avgVolume: avgVolume(vol?.volumes || []),
		};
		state.stockDetails = {
			quote,
			cagr: performance(long?.points || [], quote.price),
		};
		if (state.tab === 'stock') render();
	} catch {
		/* keep chart-only stats */
	}
}

async function fetchYahooChart(symbol, range, interval) {
	const url = state.api
		? `/api/yahoo/chart?symbol=${encodeURIComponent(symbol)}&range=${range}&interval=${interval}`
		: `${YAHOO_CHART}/${encodeURIComponent(symbol)}?interval=${interval}&range=${range}&includePrePost=true`;
	return parseYahooChart(await fetchText(url));
}

async function fetchYahooTimeseries(symbol) {
	const now = Math.floor(Date.now() / 1000);
	const start = now - 400 * 86_400;
	const types = 'trailingPeRatio,trailingMarketCap,trailingDividendYield,trailingDilutedEPS';
	const url = state.api
		? `/api/yahoo/timeseries?symbol=${encodeURIComponent(symbol)}`
		: `${YAHOO_TIMESERIES}/${encodeURIComponent(symbol)}?symbol=${encodeURIComponent(symbol)}&type=${types}&period1=${start}&period2=${now}`;
	return parseTimeseries(await fetchText(url));
}

function stockQuote() {
	const extra = state.stockDetails?.quote || {};
	const day = state.stockChart || state.quotes[state.stockSymbol] || {};
	return {
		...day,
		pe: extra.pe,
		marketCap: extra.marketCap,
		dividendYield: extra.dividendYield,
		eps: extra.eps,
		beta: extra.beta,
		avgVolume: extra.avgVolume,
		extendedLabel: day.extendedLabel || extra.extendedLabel,
		extendedPrice: day.extendedPrice ?? extra.extendedPrice,
		extendedChange: day.extendedChange ?? extra.extendedChange,
		extendedPercent: day.extendedPercent ?? extra.extendedPercent,
	};
}

function stockDetailScreen() {
	const wrap = document.createElement('div');
	wrap.className = 'stock-detail';
	const list = watchlist(state.doc);
	const item = list.find((row) => String(row.symbol).toUpperCase() === state.stockSymbol) || { symbol: state.stockSymbol, name: '' };
	const live = stockQuote();
	const points = state.stockChart?.points || [];
	const mark = state.stockScrub != null ? points[state.stockScrub] : null;
	const price = mark?.close ?? live.price ?? item.price;
	const base = scrubBaseline(points, state.stockRange, live.previousClose);
	const change = mark && base ? mark.close - base : live.change;
	const pct = mark && base ? ((mark.close - base) / base) * 100 : live.changePercent ?? item.changePercent;
	const up = (pct ?? 0) >= 0;
	const symbol = document.createElement('h2');
	symbol.textContent = live.symbol || state.stockSymbol;
	const name = document.createElement('p');
	name.className = 'hint';
	name.textContent = live.name || item.name || '';
	const priceEl = document.createElement('h2');
	priceEl.id = 'stock-price';
	priceEl.textContent = formatPrice(price, live.currency || item.currency);
	const changeEl = document.createElement('p');
	changeEl.id = 'stock-change';
	changeEl.className = `hint ${up ? 'up' : 'down'}`;
	changeEl.textContent = [formatChange(change), formatPercent(pct)].filter(Boolean).join('  ');
	wrap.append(symbol, name, priceEl, changeEl);
	const date = document.createElement('p');
	date.className = 'hint';
	date.id = 'stock-date';
	date.textContent = mark ? formatChartTime(mark.time, state.stockRange) : '';
	wrap.append(date);
	const extended = document.createElement('p');
	extended.id = 'stock-extended';
	const extLine = mark ? '' : formatExtended(live);
	extended.className = `hint ${(live.extendedChange ?? 0) >= 0 ? 'up' : 'down'}`;
	extended.textContent = extLine;
	if (!extLine) extended.classList.add('hidden');
	wrap.append(extended);
	wrap.append(stockChartEl(points, up, state.stockScrub));
	const ranges = document.createElement('div');
	ranges.className = 'ranges';
	for (const row of STOCK_RANGES) {
		const btn = button(row.label, () => {
			state.stockRange = row.label;
			state.stockScrub = null;
			state.stockChart = null;
			render();
			loadStockChart();
		}, row.label === state.stockRange ? 'primary' : 'ghost');
		ranges.append(btn);
	}
	wrap.append(ranges);
	for (const row of quoteStats(live)) wrap.append(statRow(row));
	const cagrHead = document.createElement('p');
	cagrHead.className = 'hint cagr-label';
	cagrHead.textContent = 'CAGR';
	wrap.append(cagrHead);
	for (const row of cagrStats(state.stockDetails?.cagr)) wrap.append(statRow(row));
	return wrap;
}

function statRow(row) {
	const line = document.createElement('div');
	line.className = 'stat-row';
	const left = document.createElement('div');
	left.innerHTML = `<span class="hint">${row.leftLabel}</span><div>${row.leftValue}</div>`;
	const right = document.createElement('div');
	right.className = 'stat-right';
	right.innerHTML = `<span class="hint">${row.rightLabel}</span><div>${row.rightValue}</div>`;
	line.append(left, right);
	return line;
}

function stockChartEl(points, up, selectedIndex) {
	const box = document.createElement('div');
	box.className = `stock-chart${up ? ' up' : ' down'}`;
	if (!points.length) {
		box.append(empty('Loading chart…'));
		return box;
	}
	const w = 320;
	const h = 140;
	const ys = points.map((p) => p.close);
	const min = Math.min(...ys);
	const max = Math.max(...ys);
	const span = max - min || 1;
	const dx = points.length > 1 ? w / (points.length - 1) : w;
	const coords = points.map((p, i) => {
		const x = i * dx;
		const y = h - ((p.close - min) / span) * h;
		return [x, y];
	});
	const line = coords.map((c, i) => `${i ? 'L' : 'M'}${c[0].toFixed(1)},${c[1].toFixed(1)}`).join(' ');
	const fill = `${line} L${w},${h} L0,${h} Z`;
	const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
	svg.setAttribute('viewBox', `0 0 ${w} ${h}`);
	svg.setAttribute('preserveAspectRatio', 'none');
	svg.innerHTML = `<path class="fill" d="${fill}"></path><path class="line" d="${line}"></path>`;
	if (selectedIndex != null && coords[selectedIndex]) {
		const [x, y] = coords[selectedIndex];
		svg.innerHTML += `<line class="mark" x1="${x}" x2="${x}" y1="0" y2="${h}"></line><circle class="mark" cx="${x}" cy="${y}" r="3.5"></circle>`;
	}
	bindScrub(box, (t) => {
		state.stockScrub = indexAt(t * 100, 100, points.length);
		paintStockScrub(points);
	});
	box.addEventListener('pointerup', () => {
		state.stockScrub = null;
		paintStockScrub(points);
	});
	box.append(svg);
	return box;
}

function paintStockScrub(points) {
	const live = stockQuote();
	const mark = state.stockScrub != null ? points[state.stockScrub] : null;
	const price = mark?.close ?? live.price;
	const base = scrubBaseline(points, state.stockRange, live.previousClose);
	const change = mark && base ? mark.close - base : live.change;
	const pct = mark && base ? ((mark.close - base) / base) * 100 : live.changePercent;
	const up = (pct ?? 0) >= 0;
	const priceEl = document.getElementById('stock-price');
	const changeEl = document.getElementById('stock-change');
	const dateEl = document.getElementById('stock-date');
	if (priceEl) priceEl.textContent = formatPrice(price, live.currency);
	if (changeEl) {
		changeEl.className = `hint ${up ? 'up' : 'down'}`;
		changeEl.textContent = [formatChange(change), formatPercent(pct)].filter(Boolean).join('  ');
	}
	if (dateEl) dateEl.textContent = mark ? formatChartTime(mark.time, state.stockRange) : '';
	const extEl = document.getElementById('stock-extended');
	if (extEl) {
		const line = mark ? '' : formatExtended(live);
		extEl.textContent = line;
		extEl.classList.toggle('hidden', !line);
	}
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
			setStatus(statusFromError(err));
		}
	});
	input.click();
}

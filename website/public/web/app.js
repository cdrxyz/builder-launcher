import { decrypt, encrypt, decodeUtf8, encodeUtf8 } from './crypto.js';
import { credentialsReady, ready, s3Get, s3Put } from './s3.js';
import {
	doneTodos,
	isNote,
	newId,
	noteTitle,
	notesByEdited,
	openTodos,
	seedNote,
} from './items.js';
import { renderMarkdown } from './markdown.js';

const CREDS_KEY = 'builder-launcher-web-creds';
const SNAP_KEY = 'builder-launcher-web-snapshot';
const DOCS = 'https://cdrxyz.github.io/builder-launcher/configure/web/';

const state = {
	tab: 'tasks',
	noteId: null,
	noteMode: 'edit',
	status: '',
	busy: false,
	dirty: false,
	creds: loadCreds(),
	doc: loadSnapshot(),
};

const app = document.getElementById('app');

if ('serviceWorker' in navigator) {
	navigator.serviceWorker.register('./sw.js').catch(() => {});
}

if (!readyCreds(state.creds) && !state.doc) state.tab = 'settings';
render();
if (readyCreds(state.creds) && !state.doc) pull().catch(() => {});

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

function setItems(next) {
	if (!state.doc) state.doc = { version: 1, exportedAt: Date.now(), items: [] };
	state.doc = { ...state.doc, items: next };
	state.dirty = true;
	saveSnapshot(state.doc);
	render();
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
		saveSnapshot(state.doc);
		state.status = pulledLabel(state.doc);
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
			'Upload this snapshot? The last successful upload wins. The phone will see these tasks and notes on restore.',
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
	return `Pulled ${when} · ${todos} open tasks · ${notes} notes`;
}

function render() {
	app.replaceChildren();
	app.append(header(), statusLine(), main(), tabs());
}

function header() {
	const el = document.createElement('header');
	el.className = 'app-bar';
	if (state.tab === 'note') {
		el.append(button('<', () => ((state.tab = 'notes'), (state.noteId = null), render()), 'ghost'));
		el.append(title('note'));
		el.append(
			button(state.noteMode === 'edit' ? 'view' : 'edit', () => {
				state.noteMode = state.noteMode === 'edit' ? 'view' : 'edit';
				render();
			}, 'ghost'),
		);
		return el;
	}
	el.append(button(state.busy ? '…' : 'pull', () => pull(), 'ghost'));
	el.append(title(state.tab));
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
	else el.append(tasksScreen());
	return el;
}

function tabs() {
	const el = document.createElement('footer');
	el.className = 'tab-bar';
	for (const name of ['tasks', 'notes', 'settings']) {
		el.append(
			button(name, () => {
				state.tab = name;
				state.noteId = null;
				render();
			}, `tab${state.tab === name || (name === 'notes' && state.tab === 'note') ? ' active' : ''}`),
		);
	}
	return el;
}

function tasksScreen() {
	const wrap = document.createElement('div');
	const open = openTodos(items());
	const done = doneTodos(items());
	if (!open.length && !done.length) {
		wrap.append(empty('No tasks yet. Pull from S3 or type below.'));
	}
	for (const item of open) wrap.append(todoRow(item, false));
	if (done.length) {
		const h = document.createElement('p');
		h.className = 'hint';
		h.textContent = 'done';
		h.style.marginTop = '1.25rem';
		wrap.append(h);
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
		setStatus('Cleared credentials and cache on this device.');
	}, 'ghost danger');
	actions.append(save, fileBtn, forget);
	form.append(actions);
	const note = document.createElement('p');
	note.className = 'footer-note';
	note.innerHTML = `Same fields as Settings → backup on the phone. Object key is always <code>builder-launcher/backup.enc</code>. Snapshot, not two-way sync. The bucket needs CORS for this origin. <a href="${DOCS}">Manual</a>. Built by <a href="https://cdr.xyz">Cedar Labs</a>.`;
	form.append(note);
	return form;
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
	input.placeholder = `${prefix}${placeholder}`;
	input.autocomplete = 'off';
	const go = document.createElement('button');
	go.className = 'primary';
	go.type = 'submit';
	go.textContent = prefix;
	form.append(input, go);
	form.addEventListener('submit', (event) => {
		event.preventDefault();
		const raw = input.value.trim();
		if (!raw) return;
		const text = raw.startsWith(prefix) ? raw.slice(1).trim() : raw;
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
		} catch (err) {
			setStatus(err.message || String(err));
		}
	});
	input.click();
}

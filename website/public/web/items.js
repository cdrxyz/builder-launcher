export function isTodo(item) {
	return String(item?.kind || '').toLowerCase() === 'todo';
}

export function isNote(item) {
	return String(item?.kind || '').toLowerCase() === 'note';
}

export function isDone(item) {
	return item?.completedAt != null;
}

export function editedAt(item) {
	return item?.updatedAt > 0 ? item.updatedAt : item?.createdAt || 0;
}

export function noteTitle(text) {
	const line = String(text || '')
		.split('\n')[0]
		.replace(/^#+\s*/, '')
		.trim();
	return line || 'untitled';
}

export function openTodos(items) {
	return (items || []).filter((item) => isTodo(item) && !isDone(item));
}

export function doneTodos(items) {
	return (items || [])
		.filter((item) => isTodo(item) && isDone(item))
		.sort((a, b) => (b.completedAt || 0) - (a.completedAt || 0));
}

export const TASK_COMPLETE_HOLD_MS = 1000;
export const TASK_COMPLETE_FADE_MS = 280;

function pendingIndex(pending, id) {
	if (!pending) return undefined;
	return typeof pending.get === 'function' ? pending.get(id) : pending[id];
}

/** Keep freshly completed rows in the open list until the leave animation finishes. */
export function displayOpenTodos(items, pending) {
	const open = openTodos(items).slice();
	const held = doneTodos(items)
		.filter((item) => pendingIndex(pending, item.id) != null)
		.sort((a, b) => pendingIndex(pending, a.id) - pendingIndex(pending, b.id));
	for (const item of held) {
		const at = Math.min(Math.max(pendingIndex(pending, item.id), 0), open.length);
		open.splice(at, 0, item);
	}
	return open;
}

export function displayDoneTodos(items, pending) {
	return doneTodos(items).filter((item) => pendingIndex(pending, item.id) == null);
}

export function notesByEdited(items) {
	return (items || [])
		.filter(isNote)
		.slice()
		.sort((a, b) => editedAt(b) - editedAt(a));
}

export function newId(now = Date.now()) {
	return now.toString(36);
}

export function seedNote(title = '') {
	const heading = title.trim();
	return heading ? `# ${heading}` : '# ';
}

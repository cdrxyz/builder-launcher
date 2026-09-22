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

export function taskRank(item) {
	const order = Number(item?.order) || 0;
	return order !== 0 ? order : Number(item?.createdAt) || 0;
}

export function openTodos(items) {
	return (items || [])
		.filter((item) => isTodo(item) && !isDone(item))
		.sort((a, b) => taskRank(b) - taskRank(a));
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

function isOpenTodo(item) {
	return isTodo(item) && !isDone(item);
}

/** Encode a visual sequence. Ranks stay below `now`, so a later add still sorts to the top. */
export function stampOrder(sequence, now, orderedAt = now) {
	if (!sequence.length) return sequence;
	const top = now > sequence.length ? now - 1 : sequence.length;
	return sequence.map((item, index) => ({
		...item,
		order: top - index,
		orderedAt,
	}));
}

export function moveOpenItems(items, from, to, now = Date.now()) {
	const current = openTodos(items);
	if (from === to || from < 0 || to < 0 || from >= current.length || to >= current.length) return items;
	const moved = current.slice();
	const [item] = moved.splice(from, 1);
	moved.splice(to, 0, item);
	const ranked = stampOrder(moved, now);
	let i = 0;
	return items.map((row) => (isOpenTodo(row) ? ranked[i++] : row));
}

function idSet(list) {
	return new Set((list || []).map((item) => String(item?.id)));
}

function liftUnseen(left, right, merged) {
	const leftIds = idSet(left);
	const rightIds = idSet(right);
	const open = (merged || []).filter(isOpenTodo);
	const shared = open.filter((item) => leftIds.has(String(item.id)) && rightIds.has(String(item.id)));
	const unseen = open.filter((item) => leftIds.has(String(item.id)) !== rightIds.has(String(item.id)));
	if (!shared.length || !unseen.length) return merged;
	const maxShared = Math.max(...shared.map(taskRank));
	const need = unseen
		.filter((item) => !(item.orderedAt > 0) && taskRank(item) <= maxShared)
		.sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));
	if (!need.length) return merged;
	const updates = new Map(need.map((item, index) => [String(item.id), maxShared + 1 + index]));
	return merged.map((item) => {
		const order = updates.get(String(item.id));
		return order == null ? item : { ...item, order };
	});
}

/** Open tasks first, highest rank at the top. Notes and completed rows keep their relative order. */
export function alignOpenOrder(left, right, merged) {
	const lifted = liftUnseen(left, right, merged || []);
	const open = lifted.filter(isOpenTodo).sort((a, b) => taskRank(b) - taskRank(a));
	const rest = lifted.filter((item) => !isOpenTodo(item));
	return [...open, ...rest];
}

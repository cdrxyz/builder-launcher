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

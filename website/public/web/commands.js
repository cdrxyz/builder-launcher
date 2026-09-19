export const DEFAULT_PROMPT = '>';

export const PREFIXES = [
	{ glyph: '@', label: 'text', web: false },
	{ glyph: '#', label: 'call', web: false },
	{ glyph: '*', label: 'calendar', web: false },
	{ glyph: '-', label: 'todo', web: true },
	{ glyph: '+', label: 'note', web: true },
	{ glyph: '$', label: 'stock', web: true },
	{ glyph: '?', label: 'ask AI', web: false },
	{ glyph: '/', label: 'slash', web: true },
];

export const SLASH = [
	{ name: 'help', label: 'commands' },
	{ name: 'home', label: 'home' },
	{ name: 'notes', label: 'all notes' },
	{ name: 'podcasts', label: 'all podcasts' },
	{ name: 'pull', label: 'sync down' },
	{ name: 'push', label: 'sync up' },
	{ name: 'settings', label: 'settings' },
	{ name: 'stocks', label: 'watchlist' },
	{ name: 'tasks', label: 'all tasks' },
].sort((a, b) => a.name.localeCompare(b.name));

export function findPrefix(glyph) {
	return PREFIXES.find((row) => row.glyph === glyph) || null;
}

export function isModePrompt(glyph) {
	return Boolean(findPrefix(glyph));
}

export function typeMode(prompt, input) {
	const first = input[0];
	if (first && isModePrompt(first)) return { prompt: first, input: input.slice(1) };
	return { prompt, input };
}

export function slashMatches(query) {
	const q = String(query || '')
		.trim()
		.toLowerCase();
	if (!q) return SLASH;
	return SLASH.filter((row) => row.name.startsWith(q));
}

export function slashResolve(query) {
	const q = String(query || '')
		.trim()
		.toLowerCase();
	if (!q) return null;
	const exact = SLASH.find((row) => row.name === q);
	if (exact) return exact;
	const hits = slashMatches(q);
	return hits.length === 1 ? hits[0] : null;
}

export function builtinPage(query) {
	const q = String(query || '')
		.trim()
		.toLowerCase();
	if (!q) return null;
	if (q === 'notes' || q === 'note') return 'notes';
	if (q === 'stocks' || q === 'stock') return 'stocks';
	if (q === 'podcasts' || q === 'podcast' || q === 'pods') return 'pods';
	if (q === 'settings' || q === 'setting' || q === 'backup') return 'settings';
	if (q === 'tasks' || q === 'todos' || q === 'todo') return 'tasks';
	if (q === 'home') return 'home';
	if (q === 'help') return 'help';
	if (q === 'pull' || q === 'push') return q;
	return null;
}

export function homePreview(items, limit = 3) {
	return (items || []).filter((item) => String(item.kind).toLowerCase() === 'todo' && item.completedAt == null).slice(0, limit);
}

export function pagePrompt(tab) {
	if (tab === 'tasks') return '-';
	if (tab === 'notes' || tab === 'note') return '+';
	if (tab === 'stocks') return '$';
	if (tab === 'pods' || tab === 'show' || tab === 'episode') return '>';
	if (tab === 'settings' || tab === 'help') return '/';
	return DEFAULT_PROMPT;
}

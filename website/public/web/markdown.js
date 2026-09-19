export function renderMarkdown(src) {
	const escaped = escapeHtml(String(src || ''));
	const lines = escaped.split('\n');
	const out = [];
	let list = null;
	const flush = () => {
		if (list) {
			out.push(`<ul>${list.join('')}</ul>`);
			list = null;
		}
	};
	for (const line of lines) {
		const item = line.match(/^[-*]\s+(.*)$/);
		if (item) {
			list = list || [];
			list.push(`<li>${inline(item[1])}</li>`);
			continue;
		}
		flush();
		if (!line.trim()) {
			out.push('');
			continue;
		}
		const h = line.match(/^(#{1,3})\s+(.*)$/);
		if (h) {
			const n = h[1].length;
			out.push(`<h${n}>${inline(h[2])}</h${n}>`);
			continue;
		}
		out.push(`<p>${inline(line)}</p>`);
	}
	flush();
	return out.join('\n');
}

function inline(value) {
	return value
		.replace(/`([^`]+)`/g, '<code>$1</code>')
		.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
		.replace(/\*([^*]+)\*/g, '<em>$1</em>');
}

function escapeHtml(value) {
	return value
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;');
}

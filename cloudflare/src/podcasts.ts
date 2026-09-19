type FyydShow = {
	title?: unknown;
	author?: unknown;
	xmlURL?: unknown;
	imgURL?: unknown;
	thumbImageURL?: unknown;
};

export function fyydToItunes(raw: string): string {
	let root: { data?: unknown };
	try {
		root = JSON.parse(raw) as { data?: unknown };
	} catch {
		return JSON.stringify({ resultCount: 0, results: [] });
	}
	const rows = Array.isArray(root.data) ? (root.data as FyydShow[]) : [];
	const results = rows
		.slice(0, 8)
		.map((row) => {
			const feedUrl = String(row?.xmlURL || '').trim();
			const collectionName = String(row?.title || '').trim();
			if (!feedUrl || !collectionName) return null;
			const artwork = String(row?.imgURL || row?.thumbImageURL || '').trim();
			return {
				collectionName,
				artistName: String(row?.author || '').trim(),
				feedUrl,
				artworkUrl600: artwork,
				artworkUrl100: artwork,
			};
		})
		.filter(Boolean);
	return JSON.stringify({ resultCount: results.length, results });
}

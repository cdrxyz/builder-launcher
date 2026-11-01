const CACHE = 'builder-launcher-web-v60';
const SHELL = [
	'./',
	'./index.html',
	'./app.css',
	'./app.js',
	'./s3.js',
	'./crypto.js',
	'./items.js',
	'./markdown.js',
	'./ai.js',
	'./media.js',
	'./commands.js',
	'./reads.js',
	'./videos.js',
	'./article-audio.js',
	'./account.js',
	'./merge.js',
	'./weather.js',
	'./manifest.webmanifest',
	'./icon-192.png',
	'./icon-512.png',
];

self.addEventListener('install', (event) => {
	event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
	event.waitUntil(
		caches
			.keys()
			.then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
			.then(() => self.clients.claim()),
	);
});

const liveStreams = new Map();

self.addEventListener('message', (event) => {
	const data = event.data || {};
	if (data.type !== 'live-audio') return;
	const slot = liveStreams.get(`${data.id}:${data.token}`);
	if (!slot) return;
	if (data.done) {
		try { slot.controller.close(); } catch { /* already closed */ }
		liveStreams.delete(`${data.id}:${data.token}`);
		return;
	}
	if (data.chunk) {
		const bytes = data.chunk instanceof Uint8Array ? data.chunk : new Uint8Array(data.chunk);
		try { slot.controller.enqueue(bytes); } catch { /* the player stopped */ }
	}
});

self.addEventListener('fetch', (event) => {
	const req = event.request;
	if (req.method !== 'GET') return;
	const url = new URL(req.url);
	if (url.origin !== self.location.origin) return;
	if (url.pathname === '/api/articles/audio') return;
	if (url.pathname === '/api/videos/stream') return;
	if (url.pathname === '/api/articles/live') {
		const id = url.searchParams.get('id') || '';
		const token = url.searchParams.get('t') || '';
		const key = `${id}:${token}`;
		const stream = new ReadableStream({
			start(controller) {
				liveStreams.set(key, { controller });
				self.clients.matchAll({ includeUncontrolled: true }).then((clients) => {
					clients.forEach((client) => client.postMessage({ type: 'live-open', id, token }));
				});
			},
			cancel() {
				liveStreams.delete(key);
			},
		});
		event.respondWith(new Response(stream, {
			headers: { 'content-type': 'audio/mpeg', 'cache-control': 'no-store' },
		}));
		return;
	}
	if (url.pathname.startsWith('/api/')) {
		event.respondWith(fetch(req));
		return;
	}
	event.respondWith(
		caches.match(req).then((hit) => hit || fetch(req)),
	);
});

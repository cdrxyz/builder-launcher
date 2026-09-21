const CACHE = 'builder-launcher-web-v23';
const SHELL = [
	'./',
	'./index.html',
	'./app.css',
	'./app.js',
	'./s3.js',
	'./crypto.js',
	'./items.js',
	'./markdown.js',
	'./media.js',
	'./commands.js',
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

self.addEventListener('fetch', (event) => {
	const req = event.request;
	if (req.method !== 'GET') return;
	const url = new URL(req.url);
	if (url.origin !== self.location.origin) return;
	if (url.pathname.startsWith('/api/')) {
		event.respondWith(fetch(req));
		return;
	}
	event.respondWith(
		caches.match(req).then((hit) => hit || fetch(req)),
	);
});

import { jsonError } from './safe';
import { pageStreamUrl, pickStreams, playerRequest, streamHostAllowed, youtubeId } from '../../website/public/web/videos.js';

export async function handleVideos(request: Request, url: URL): Promise<Response | null> {
	if (url.pathname === '/api/videos/stream' && request.method === 'GET') return streamVideo(request, url);
	if (url.pathname !== '/api/videos/resolve' || request.method !== 'GET') return null;
	const id = youtubeId(url.searchParams.get('url') || url.searchParams.get('id') || '');
	if (!id) return jsonError('Paste a YouTube URL.');
	const call = playerRequest(id);
	let player: unknown;
	try {
		const res = await fetch(call.url, {
			method: 'POST',
			headers: call.headers,
			body: call.body,
			signal: AbortSignal.timeout(20_000),
		});
		if (!res.ok) return jsonError('Could not reach YouTube.', 502);
		player = await res.json();
	} catch {
		return jsonError('Could not reach YouTube.', 502);
	}
	const picked = pickStreams(player);
	if (!picked.ok) return jsonError(picked.reason || 'Could not play that video.', 422);
	return Response.json({
		...picked,
		audioUrl: pageStreamUrl('audio', id, picked.audioUrl),
		videoUrl: pageStreamUrl('video', id, picked.videoUrl),
	});
}

async function streamVideo(request: Request, url: URL): Promise<Response> {
	const src = url.searchParams.get('src') || '';
	if (!streamHostAllowed(src)) return jsonError('Stream host refused.', 403);
	const headers = new Headers();
	const range = request.headers.get('range');
	if (range) headers.set('range', range);
	headers.set('user-agent', 'com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip');
	let upstream: Response;
	try {
		upstream = await fetch(src, { headers });
	} catch {
		return jsonError('Could not reach the stream.', 502);
	}
	const out = new Headers();
	for (const name of ['content-type', 'content-length', 'content-range', 'accept-ranges']) {
		const value = upstream.headers.get(name);
		if (value) out.set(name, value);
	}
	out.set('cache-control', 'no-store');
	return new Response(upstream.body, { status: upstream.status, headers: out });
}

import { jsonError } from './safe';
import { pickStreams, playerRequest, youtubeId } from '../../website/public/web/videos.js';

export async function handleVideos(request: Request, url: URL): Promise<Response | null> {
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
	return Response.json(picked);
}

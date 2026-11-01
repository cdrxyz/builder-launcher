import assert from 'node:assert/strict';
import test from 'node:test';
import { addVideo, pickStreams, youtubeId } from '../public/web/videos.js';

test('a youtube link becomes an id', () => {
	assert.equal(youtubeId('https://www.youtube.com/watch?v=dQw4w9WgXcQ'), 'dQw4w9WgXcQ');
	assert.equal(youtubeId('https://youtu.be/dQw4w9WgXcQ'), 'dQw4w9WgXcQ');
	assert.equal(youtubeId('https://www.youtube.com/shorts/dQw4w9WgXcQ'), 'dQw4w9WgXcQ');
	assert.equal(youtubeId('not a video'), '');
});

test('a player response keeps audio separate from the picture', () => {
	const picked = pickStreams({
		playabilityStatus: { status: 'OK' },
		videoDetails: { videoId: 'dQw4w9WgXcQ', title: 'Song', author: 'Artist', lengthSeconds: '213' },
		streamingData: {
			formats: [{ mimeType: 'video/mp4; codecs="avc1, mp4a.40.2"', bitrate: 400000, url: 'https://example.test/muxed' }],
			adaptiveFormats: [
				{ mimeType: 'audio/webm; codecs="opus"', bitrate: 160000, url: 'https://example.test/opus' },
				{ mimeType: 'audio/mp4; codecs="mp4a.40.2"', bitrate: 128000, url: 'https://example.test/audio' },
				{ mimeType: 'video/mp4; codecs="avc1"', bitrate: 1000000, url: 'https://example.test/720' },
			],
		},
	});
	assert.equal(picked.ok, true);
	assert.equal(picked.audioUrl, 'https://example.test/audio');
	assert.equal(picked.videoUrl, 'https://example.test/muxed');
	assert.equal(picked.muxed, true);
	assert.equal(picked.durationMs, 213000);
	assert.equal(pickStreams({ playabilityStatus: { status: 'LOGIN_REQUIRED', reason: 'Sign in' } }).ok, false);
});

test('saving a video replaces the same id', () => {
	const first = addVideo([], { id: 'vdQw4w9WgXcQ', url: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', title: 'Old' }, 1);
	const next = addVideo(first, { id: 'vdQw4w9WgXcQ', url: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', title: 'New' }, 2);
	assert.equal(next.length, 1);
	assert.equal(next[0].title, 'New');
});

test('only a googlevideo stream can be proxied', async () => {
	const { streamHostAllowed, pageStreamUrl } = await import('../public/web/videos.js');
	assert.equal(streamHostAllowed('https://rr5.googlevideo.com/videoplayback?id=1'), true);
	assert.equal(streamHostAllowed('https://evil.example/rr5.googlevideo.com'), false);
	assert.equal(streamHostAllowed('not a url'), false);
	assert.match(pageStreamUrl('audio', 'dQw4w9WgXcQ', 'https://rr5.googlevideo.com/a'), /^\/api\/videos\/stream\?kind=audio&id=dQw4w9WgXcQ&src=/);
});

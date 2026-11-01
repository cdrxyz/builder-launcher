export const SAVED_FEED = 'saved-videos';
export const MAX_VIDEOS = 100;
export const PLAYER_KEY = 'AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w';
export const PLAYER_UA = 'com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip';

const ID = /^[A-Za-z0-9_-]{11}$/;

export function youtubeId(raw) {
	const text = String(raw || '').trim();
	if (!text) return '';
	if (ID.test(text)) return text;
	let url;
	try {
		url = new URL(text.includes('://') ? text : `https://${text}`);
	} catch {
		return '';
	}
	const host = url.hostname.replace(/^www\./, '').toLowerCase();
	if (host === 'youtu.be') {
		const id = url.pathname.split('/').filter(Boolean)[0] || '';
		return ID.test(id) ? id : '';
	}
	if (host !== 'youtube.com' && host !== 'm.youtube.com' && host !== 'music.youtube.com') return '';
	const fromQuery = url.searchParams.get('v') || '';
	if (ID.test(fromQuery)) return fromQuery;
	const parts = url.pathname.split('/').filter(Boolean);
	const marker = parts.findIndex((part) => part === 'shorts' || part === 'embed' || part === 'live' || part === 'v');
	const id = marker >= 0 ? parts[marker + 1] || '' : '';
	return ID.test(id) ? id : '';
}

export function canonicalUrl(id) {
	return ID.test(id) ? `https://www.youtube.com/watch?v=${id}` : '';
}

export function playerRequest(id) {
	return {
		url: `https://www.youtube.com/youtubei/v1/player?key=${PLAYER_KEY}&prettyPrint=false`,
		headers: {
			'content-type': 'application/json',
			'user-agent': PLAYER_UA,
		},
		body: JSON.stringify({
			context: {
				client: {
					clientName: 'ANDROID',
					clientVersion: '20.10.38',
					hl: 'en',
					gl: 'US',
					androidSdkVersion: 34,
				},
			},
			videoId: id,
			contentCheckOk: true,
			racyCheckOk: true,
		}),
	};
}

function mime(format) {
	return String(format?.mimeType || '').split(';')[0].trim().toLowerCase();
}

function playable(formats) {
	return (formats || []).filter((format) => format?.url && !format.signatureCipher && !format.cipher);
}

function best(formats, test) {
	return playable(formats).filter(test).sort((a, b) => (b.bitrate || 0) - (a.bitrate || 0))[0] || null;
}

export function pickStreams(player) {
	const status = player?.playabilityStatus?.status || '';
	if (status !== 'OK') {
		return {
			ok: false,
			reason: String(player?.playabilityStatus?.reason || 'Could not play that video.'),
		};
	}
	const details = player.videoDetails || {};
	const streaming = player.streamingData || {};
	const formats = [...(streaming.formats || []), ...(streaming.adaptiveFormats || [])];
	const audio = best(formats, (format) => mime(format) === 'audio/mp4')
		|| best(formats, (format) => mime(format).startsWith('audio/'));
	const muxed = best(formats, (format) => mime(format).startsWith('video/') && /mp4a|opus/.test(String(format.mimeType || '')));
	const picture = muxed || best(formats, (format) => mime(format) === 'video/mp4')
		|| best(formats, (format) => mime(format).startsWith('video/'));
	if (!audio && !picture) return { ok: false, reason: 'No playable stream.' };
	const seconds = Number(details.lengthSeconds || 0);
	return {
		ok: true,
		id: String(details.videoId || ''),
		title: String(details.title || 'Video'),
		author: String(details.author || ''),
		durationMs: Number.isFinite(seconds) && seconds > 0 ? Math.round(seconds * 1000) : 0,
		audioUrl: audio?.url || picture?.url || '',
		audioType: audio ? mime(audio) : mime(picture),
		videoUrl: picture?.url || '',
		videoType: picture ? mime(picture) : '',
		muxed: Boolean(muxed),
	};
}

export function videosOf(doc) {
	const bag = doc?.videos || {};
	return {
		videos: Array.isArray(bag.videos) ? bag.videos : [],
		progress: Array.isArray(bag.progress) ? bag.progress : [],
		deletedIds: Array.isArray(bag.deletedIds) ? bag.deletedIds : [],
	};
}

export function sortedVideos(videos) {
	return [...(videos || [])].sort((a, b) => (b.addedAt || 0) - (a.addedAt || 0));
}

export function addVideo(videos, video, now = Date.now()) {
	const next = {
		id: video.id,
		url: video.url,
		title: video.title || 'Video',
		author: video.author || '',
		durationMs: video.durationMs || 0,
		addedAt: now,
	};
	const kept = (videos || []).filter((item) => item.id !== next.id);
	return sortedVideos([next, ...kept]).slice(0, MAX_VIDEOS);
}

export function deletedIdsAfterAdd(deletedIds, id) {
	return (deletedIds || []).filter((item) => item !== id);
}

export function mergeVideos(a, b, restoredIds = []) {
	const restored = new Set(restoredIds.map(String));
	const deleted = [...new Set([...(a?.deletedIds || []), ...(b?.deletedIds || [])])].filter((id) => !restored.has(String(id)));
	const drop = new Set(deleted.map(String));
	const videos = new Map();
	for (const item of [...(a?.videos || []), ...(b?.videos || [])]) {
		if (!item?.id || drop.has(String(item.id))) continue;
		const prev = videos.get(item.id);
		videos.set(item.id, !prev || (item.addedAt || 0) >= (prev.addedAt || 0) ? item : prev);
	}
	return {
		videos: sortedVideos([...videos.values()]),
		progress: [],
		deletedIds: deleted,
	};
}

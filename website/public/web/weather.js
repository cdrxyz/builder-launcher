export const GEOCODE = 'https://geocoding-api.open-meteo.com/v1/search';
export const FORECAST = 'https://api.open-meteo.com/v1/forecast';

export function kindOfCode(code) {
	const n = Number(code);
	if (n === 0) return 'CLEAR';
	if (n === 1 || n === 2) return 'FAIR';
	if (n === 3) return 'CLOUDY';
	if (n === 45 || n === 48) return 'FOG';
	if (n >= 51 && n <= 57) return 'DRIZZLE';
	if ((n >= 61 && n <= 67) || (n >= 80 && n <= 82)) return 'RAIN';
	if ((n >= 71 && n <= 77) || (n >= 85 && n <= 86)) return 'SNOW';
	if (n >= 95 && n <= 99) return 'STORM';
	return null;
}

export function weatherLabel(code) {
	const kind = kindOfCode(code);
	if (!kind) return '—';
	return kind.toLowerCase();
}

export function displayTemperature(celsius, units = 'METRIC') {
	const c = Number(celsius);
	if (!Number.isFinite(c)) return null;
	if (String(units).toUpperCase() === 'IMPERIAL') return Math.round((c * 9) / 5 + 32);
	return Math.round(c);
}

export function homeTemperature(celsius, units = 'METRIC') {
	const t = displayTemperature(celsius, units);
	return t == null ? '' : `${t}°`;
}

export function parseGeocode(raw, limit = 6) {
	let root;
	try {
		root = JSON.parse(raw);
	} catch {
		return [];
	}
	const results = Array.isArray(root?.results) ? root.results : [];
	const out = [];
	for (const obj of results) {
		const name = String(obj?.name || '').trim();
		const lat = Number(obj?.latitude);
		const lon = Number(obj?.longitude);
		if (!name || !Number.isFinite(lat) || !Number.isFinite(lon)) continue;
		const admin = String(obj?.admin1 || '').trim();
		const country = String(obj?.country || '').trim();
		out.push({
			name,
			latitude: lat,
			longitude: lon,
			label: [name, admin, country].filter(Boolean).filter((part, i, all) => all.indexOf(part) === i).join(', '),
			timezone: obj?.timezone || null,
		});
		if (out.length >= limit) break;
	}
	return out;
}

export function parseForecast(raw, fetchedAt = Date.now()) {
	let j;
	try {
		j = JSON.parse(raw);
	} catch {
		return null;
	}
	const c = j?.current;
	if (!c || c.temperature_2m == null || c.weather_code == null) return null;
	const dailyTimes = Array.isArray(j.daily?.time) ? j.daily.time : [];
	const hourlyTimes = Array.isArray(j.hourly?.time) ? j.hourly.time : [];
	return {
		fetchedAt,
		latitude: Number(j.latitude) || 0,
		longitude: Number(j.longitude) || 0,
		timezone: j.timezone || 'UTC',
		current: {
			temperatureC: Math.round(Number(c.temperature_2m)),
			feelsC: Math.round(Number(c.apparent_temperature ?? c.temperature_2m)),
			code: Number(c.weather_code),
			humidity: numOrNull(c.relative_humidity_2m),
			precipMm: numOrNull(c.precipitation),
			windKmh: numOrNull(c.wind_speed_10m),
			isDay: c.is_day === 1,
		},
		hourly: hourlyTimes.slice(0, 24).map((time, i) => ({
			time,
			temperatureC: Math.round(Number(j.hourly.temperature_2m?.[i])),
			code: Number(j.hourly.weather_code?.[i]),
			isDay: j.hourly.is_day?.[i] === 1,
		})),
		daily: dailyTimes.slice(0, 7).map((date, i) => ({
			date,
			code: Number(j.daily.weather_code?.[i]),
			highC: Math.round(Number(j.daily.temperature_2m_max?.[i])),
			lowC: Math.round(Number(j.daily.temperature_2m_min?.[i])),
			precipProb: numOrNull(j.daily.precipitation_probability_max?.[i]),
		})),
	};
}

function numOrNull(value) {
	const n = Number(value);
	return Number.isFinite(n) ? n : null;
}

export function weatherGlyphSvg(kind, isDay = true) {
	const stroke = 'currentColor';
	if (kind === 'CLEAR' && !isDay) {
		return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14 6a7 7 0 1 0 4 12 8 8 0 1 1-4-12z" fill="none" stroke="${stroke}" stroke-width="1.6" stroke-linecap="round"/></svg>`;
	}
	if (kind === 'CLEAR') {
		return `<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4" fill="none" stroke="${stroke}" stroke-width="1.6"/><path d="M12 3v2.2M12 18.8V21M3 12h2.2M18.8 12H21M5.6 5.6l1.6 1.6M16.8 16.8l1.6 1.6M5.6 18.4l1.6-1.6M16.8 7.2l1.6-1.6" fill="none" stroke="${stroke}" stroke-width="1.6" stroke-linecap="round"/></svg>`;
	}
	if (kind === 'FAIR') {
		return `<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="16.5" cy="7.5" r="2.4" fill="none" stroke="${stroke}" stroke-width="1.5"/><path d="M5 16.2h11.2a3.2 3.2 0 0 0 .2-6.4 4.6 4.6 0 0 0-8.8 1.3A3.4 3.4 0 0 0 5 16.2z" fill="none" stroke="${stroke}" stroke-width="1.5" stroke-linejoin="round"/></svg>`;
	}
	if (kind === 'FOG') {
		return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 9.5h16M5 12.5h14M6 15.5h12" fill="none" stroke="${stroke}" stroke-width="1.6" stroke-linecap="round"/></svg>`;
	}
	if (kind === 'DRIZZLE') {
		return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.2h12a3 3 0 0 0 .2-6 4.4 4.4 0 0 0-8.4 1.2A3.2 3.2 0 0 0 5 12.2z" fill="none" stroke="${stroke}" stroke-width="1.5"/><path d="M8 16v1.6M12 16.4v1.6M16 16v1.6" fill="none" stroke="${stroke}" stroke-width="1.5" stroke-linecap="round" stroke-dasharray="0.2 2"/></svg>`;
	}
	if (kind === 'RAIN') {
		return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.2h12a3 3 0 0 0 .2-6 4.4 4.4 0 0 0-8.4 1.2A3.2 3.2 0 0 0 5 12.2z" fill="none" stroke="${stroke}" stroke-width="1.5"/><path d="M8 15.4l-1 3M12 15.4l-1 3M16 15.4l-1 3" fill="none" stroke="${stroke}" stroke-width="1.5" stroke-linecap="round"/></svg>`;
	}
	if (kind === 'SNOW') {
		return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.2h12a3 3 0 0 0 .2-6 4.4 4.4 0 0 0-8.4 1.2A3.2 3.2 0 0 0 5 12.2z" fill="none" stroke="${stroke}" stroke-width="1.5"/><path d="M8.2 16.2h0M12 16.8h0M15.6 16.2h0" fill="none" stroke="${stroke}" stroke-width="2.4" stroke-linecap="round"/></svg>`;
	}
	if (kind === 'STORM') {
		return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 11.6h12a3 3 0 0 0 .2-6 4.4 4.4 0 0 0-8.4 1.2A3.2 3.2 0 0 0 5 11.6z" fill="none" stroke="${stroke}" stroke-width="1.5"/><path d="M12.4 12.2 9.6 17h3.2L10.6 21" fill="none" stroke="${stroke}" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round"/></svg>`;
	}
	return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 16.2h12.4a3.2 3.2 0 0 0 .2-6.4 4.6 4.6 0 0 0-8.8 1.3A3.4 3.4 0 0 0 5 16.2z" fill="none" stroke="${stroke}" stroke-width="1.5" stroke-linejoin="round"/></svg>`;
}

export function podcastMarkSvg(mark) {
	const stroke = 'currentColor';
	if (mark === 'PAUSE') {
		return `<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="7" y="5" width="3.2" height="14" rx="0.6" fill="${stroke}"/><rect x="13.8" y="5" width="3.2" height="14" rx="0.6" fill="${stroke}"/></svg>`;
	}
	if (mark === 'PLAY') {
		return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5.5v13l11-6.5z" fill="${stroke}"/></svg>`;
	}
	return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6.5 13.2V12a5.5 5.5 0 0 1 11 0v1.2" fill="none" stroke="${stroke}" stroke-width="1.7" stroke-linecap="round"/><rect x="4.6" y="12.4" width="4.2" height="7" rx="1.2" fill="none" stroke="${stroke}" stroke-width="1.7"/><rect x="15.2" y="12.4" width="4.2" height="7" rx="1.2" fill="none" stroke="${stroke}" stroke-width="1.7"/></svg>`;
}

export function weekdayShort(isoDate) {
	const d = new Date(`${isoDate}T12:00:00`);
	if (Number.isNaN(d.getTime())) return isoDate;
	return d.toLocaleDateString([], { weekday: 'short' });
}

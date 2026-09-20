export const GEOCODE = 'https://geocoding-api.open-meteo.com/v1/search';
export const FORECAST = 'https://api.open-meteo.com/v1/forecast';
export const AIR_QUALITY = 'https://air-quality-api.open-meteo.com/v1/air-quality';
export const FORECAST_DAYS = 7;
export const FORECAST_CURRENT =
	'temperature_2m,apparent_temperature,weather_code,relative_humidity_2m,precipitation,wind_speed_10m,wind_direction_10m,wind_gusts_10m,surface_pressure,visibility,cloud_cover,is_day,dew_point_2m';
export const FORECAST_HOURLY = 'temperature_2m,weather_code,precipitation_probability,uv_index,is_day';
export const FORECAST_DAILY =
	'weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,sunrise,sunset,uv_index_max';

const DIRS = ['N', 'NNE', 'NE', 'ENE', 'E', 'ESE', 'SE', 'SSE', 'S', 'SSW', 'SW', 'WSW', 'W', 'WNW', 'NW', 'NNW'];

export function forecastUrl(lat, lon) {
	const qs = new URLSearchParams({
		latitude: String(lat),
		longitude: String(lon),
		current: FORECAST_CURRENT,
		hourly: FORECAST_HOURLY,
		daily: FORECAST_DAILY,
		forecast_days: String(FORECAST_DAYS),
		timezone: 'auto',
		temperature_unit: 'celsius',
		wind_speed_unit: 'kmh',
		precipitation_unit: 'mm',
	});
	return `${FORECAST}?${qs}`;
}

export function airUrl(lat, lon) {
	const qs = new URLSearchParams({
		latitude: String(lat),
		longitude: String(lon),
		current: 'us_aqi,european_aqi',
	});
	return `${AIR_QUALITY}?${qs}`;
}

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

export function displayWind(kmh, units = 'METRIC') {
	const n = Number(kmh);
	if (!Number.isFinite(n)) return null;
	if (String(units).toUpperCase() === 'IMPERIAL') return Math.round(n * 0.621371);
	return Math.round(n);
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

export function parseAqi(raw) {
	let j;
	try {
		j = JSON.parse(raw);
	} catch {
		return null;
	}
	const current = j?.current || {};
	const us = Number(current.us_aqi);
	if (Number.isFinite(us)) return Math.round(us);
	const eu = Number(current.european_aqi);
	if (Number.isFinite(eu)) return Math.round(eu);
	return null;
}

export function parseForecast(raw, fetchedAt = Date.now(), aqi = null) {
	let j;
	try {
		j = JSON.parse(raw);
	} catch {
		return null;
	}
	const c = j?.current;
	if (!c || c.temperature_2m == null || c.weather_code == null) return null;
	const lat = Number(j.latitude);
	const lon = Number(j.longitude);
	if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null;
	const tz = j.timezone || 'UTC';
	const hourly = parseHourly(j.hourly, tz, fetchedAt);
	const nowHour = hourly.reduce((best, hour) => {
		if (!best) return hour;
		return Math.abs(hour.epochMs - fetchedAt) < Math.abs(best.epochMs - fetchedAt) ? hour : best;
	}, null);
	const temp = Number(c.temperature_2m);
	if (!Number.isFinite(temp)) return null;
	const daily = parseDaily(j.daily);
	if (!daily.length) return null;
	return {
		fetchedAt,
		latitude: lat,
		longitude: lon,
		timezone: tz,
		aqi: aqi == null ? null : Number.isFinite(Number(aqi)) ? Math.round(Number(aqi)) : null,
		current: {
			temperatureC: Math.round(temp),
			feelsC: Math.round(Number(c.apparent_temperature ?? temp)),
			code: Number(c.weather_code),
			humidity: intOrNull(c.relative_humidity_2m),
			precipMm: numOrNull(c.precipitation),
			precipProb: nowHour?.precipProb ?? null,
			windKmh: numOrNull(c.wind_speed_10m),
			windDir: intOrNull(c.wind_direction_10m),
			gustKmh: numOrNull(c.wind_gusts_10m),
			pressureHpa: numOrNull(c.surface_pressure),
			visibilityM: numOrNull(c.visibility),
			cloud: intOrNull(c.cloud_cover),
			dewC: intOrNull(c.dew_point_2m),
			uv: nowHour?.uv ?? numOrNull(c.uv_index),
			isDay: c.is_day === 1,
		},
		hourly,
		daily,
	};
}

function parseHourly(obj, tz, fetchedAt) {
	if (!obj) return [];
	const times = asArray(obj.time);
	const temps = asArray(obj.temperature_2m);
	const codes = asArray(obj.weather_code);
	const probs = asArray(obj.precipitation_probability);
	const uvs = asArray(obj.uv_index);
	const days = asArray(obj.is_day);
	const out = [];
	for (let i = 0; i < times.length; i++) {
		const epoch = parseLocalMs(times[i], tz);
		if (epoch == null || epoch + 60 * 60_000 < fetchedAt) continue;
		const temp = Number(temps[i]);
		const code = Number(codes[i]);
		if (!Number.isFinite(temp) || !Number.isFinite(code)) continue;
		out.push({
			epochMs: epoch,
			time: times[i],
			temperatureC: Math.round(temp),
			code,
			precipProb: intOrNull(probs[i]),
			uv: numOrNull(uvs[i]),
			isDay: days[i] === 1,
		});
		if (out.length >= 24) break;
	}
	return out;
}

function parseDaily(obj) {
	if (!obj) return [];
	const dates = asArray(obj.time);
	const codes = asArray(obj.weather_code);
	const highs = asArray(obj.temperature_2m_max);
	const lows = asArray(obj.temperature_2m_min);
	const probs = asArray(obj.precipitation_probability_max);
	const sunrises = asArray(obj.sunrise);
	const sunsets = asArray(obj.sunset);
	const uvs = asArray(obj.uv_index_max);
	const precip = asArray(obj.precipitation_sum);
	const out = [];
	for (let i = 0; i < dates.length; i++) {
		const date = dates[i];
		const code = Number(codes[i]);
		const high = Number(highs[i]);
		const low = Number(lows[i]);
		if (!date || !Number.isFinite(code) || !Number.isFinite(high) || !Number.isFinite(low)) continue;
		out.push({
			date,
			code,
			highC: Math.round(high),
			lowC: Math.round(low),
			precipProb: intOrNull(probs[i]),
			sunrise: clockOf(sunrises[i]),
			sunset: clockOf(sunsets[i]),
			uv: numOrNull(uvs[i]),
			precipMm: numOrNull(precip[i]),
		});
		if (out.length >= FORECAST_DAYS) break;
	}
	return out;
}

export function clockOf(raw) {
	if (!raw) return null;
	const t = String(raw).includes('T') ? String(raw).slice(String(raw).indexOf('T') + 1) : String(raw);
	const m = t.match(/^(\d{2}:\d{2})/);
	return m ? m[1] : null;
}

export function hourLabel(epochMs, zone) {
	try {
		const parts = new Intl.DateTimeFormat('en-US', {
			timeZone: zone || 'UTC',
			hour: '2-digit',
			hourCycle: 'h23',
		}).formatToParts(new Date(epochMs));
		const hour = parts.find((p) => p.type === 'hour')?.value || '00';
		return hour.padStart(2, '0');
	} catch {
		return String(new Date(epochMs).getUTCHours()).padStart(2, '0');
	}
}

export function weekday(date, today = null) {
	if (today && date === today) return 'Today';
	const d = new Date(`${date}T12:00:00Z`);
	if (Number.isNaN(d.getTime())) return date;
	return new Intl.DateTimeFormat('en-US', { weekday: 'short', timeZone: 'UTC' }).format(d);
}

export function weekdayShort(isoDate) {
	return weekday(isoDate);
}

export function todayIso() {
	const n = new Date();
	return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`;
}

export function compass(deg) {
	if (deg == null || !Number.isFinite(Number(deg))) return '';
	const i = Math.round((Number(deg) % 360) / 22.5) % 16;
	return DIRS[(i + 16) % 16];
}

export function uvLabel(uv) {
	if (uv == null || !Number.isFinite(Number(uv))) return '—';
	const n = Math.round(Number(uv));
	const band = n <= 2 ? 'low' : n <= 5 ? 'mod' : n <= 7 ? 'high' : n <= 10 ? 'vhigh' : 'ext';
	return `${n} ${band}`;
}

export function aqiLabel(aqi) {
	if (aqi == null || !Number.isFinite(Number(aqi))) return '—';
	const n = Math.round(Number(aqi));
	const band = n <= 50 ? 'good' : n <= 100 ? 'mod' : n <= 150 ? 'usg' : n <= 200 ? 'unh' : n <= 300 ? 'vunh' : 'haz';
	return `${n} ${band}`;
}

export function temp(c, units) {
	const t = displayTemperature(c, units);
	return t == null ? '—' : `${t}°`;
}

export function wind(kmh, dir, units) {
	const speed = displayWind(kmh, units);
	if (speed == null) return '—';
	const unit = String(units).toUpperCase() === 'IMPERIAL' ? 'mph' : 'km/h';
	const c = compass(dir);
	return c ? `${speed} ${unit} ${c}` : `${speed} ${unit}`;
}

export function precipChance(prob) {
	return prob == null || !Number.isFinite(Number(prob)) ? '—' : `${Math.round(Number(prob))}%`;
}

export function precipAmount(mm, units) {
	const n = Number(mm);
	if (!Number.isFinite(n) || n < 0.5) return null;
	if (String(units).toUpperCase() === 'IMPERIAL') {
		const inches = n / 25.4;
		if (inches < 0.05) return null;
		const shown = inches < 1.0 ? inches.toFixed(2) : inches.toFixed(1);
		return `${shown} in`;
	}
	const shown = n < 10 ? n.toFixed(1) : String(Math.round(n));
	return `${shown} mm`;
}

export function daySummary(day, units) {
	const kind = kindOfCode(day.code);
	const chance = day.precipProb >= 20 ? `${day.precipProb}%` : null;
	const amount = precipAmount(day.precipMm, units);
	const uv = Math.round(Number(day.uv));
	const uvBit = Number.isFinite(uv) && uv >= 8 && (kind === 'CLEAR' || kind === 'FAIR') ? `UV ${uv}` : null;
	const label = weatherLabel(day.code);
	let bits;
	if (kind === 'STORM') bits = ['storms', amount || chance].filter(Boolean);
	else if (kind === 'FOG') bits = ['fog', amount || chance].filter(Boolean);
	else if (kind === 'SNOW' || kind === 'RAIN' || kind === 'DRIZZLE') {
		bits = [chance, amount].filter(Boolean);
		if (!bits.length) bits = [label];
	} else if (kind === 'CLEAR' || kind === 'FAIR') {
		bits = [amount == null ? label : null, amount == null && uvBit == null ? chance : null, amount, uvBit].filter(Boolean);
	} else {
		bits = [amount == null ? label : null, chance, amount].filter(Boolean);
	}
	return bits.join(' · ') || label;
}

export function humidity(value) {
	return value == null || !Number.isFinite(Number(value)) ? '—' : `${Math.round(Number(value))}%`;
}

export function pressure(hpa, units) {
	const n = Number(hpa);
	if (!Number.isFinite(n)) return '—';
	if (String(units).toUpperCase() === 'IMPERIAL') return `${(n * 0.02953).toFixed(2)} inHg`;
	return `${Math.round(n)} hPa`;
}

export function visibility(meters, units) {
	const n = Number(meters);
	if (!Number.isFinite(n)) return '—';
	if (String(units).toUpperCase() === 'IMPERIAL') {
		const miles = n / 1609.344;
		return miles >= 10 ? `${Math.round(miles)} mi` : `${miles.toFixed(1)} mi`;
	}
	const km = n / 1000;
	return km >= 10 ? `${Math.round(km)} km` : `${km.toFixed(1)} km`;
}

export function cloud(value) {
	return value == null || !Number.isFinite(Number(value)) ? '—' : `${Math.round(Number(value))}%`;
}

function asArray(value) {
	return Array.isArray(value) ? value : [];
}

function numOrNull(value) {
	const n = Number(value);
	return Number.isFinite(n) ? n : null;
}

function intOrNull(value) {
	const n = Number(value);
	return Number.isFinite(n) ? Math.round(n) : null;
}

function parseLocalMs(raw, tz) {
	const m = String(raw).match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
	if (!m) return null;
	const y = Number(m[1]);
	const mo = Number(m[2]);
	const d = Number(m[3]);
	const h = Number(m[4]);
	const mi = Number(m[5]);
	try {
		let guess = Date.UTC(y, mo - 1, d, h, mi);
		for (let i = 0; i < 3; i++) {
			const parts = wallParts(guess, tz);
			const asUtc = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute);
			const offset = asUtc - guess;
			const next = Date.UTC(y, mo - 1, d, h, mi) - offset;
			if (next === guess) return next;
			guess = next;
		}
		return guess;
	} catch {
		return Date.parse(`${m[1]}-${m[2]}-${m[3]}T${m[4]}:${m[5]}:00`);
	}
}

function wallParts(ms, tz) {
	const fmt = new Intl.DateTimeFormat('en-US', {
		timeZone: tz,
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
		hour: '2-digit',
		minute: '2-digit',
		hourCycle: 'h23',
	});
	const map = {};
	for (const p of fmt.formatToParts(new Date(ms))) {
		if (p.type !== 'literal') map[p.type] = p.value;
	}
	return { year: +map.year, month: +map.month, day: +map.day, hour: +map.hour, minute: +map.minute };
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

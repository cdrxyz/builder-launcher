---
title: Web
description: Notes, tasks, stocks, and podcasts on laptop or iPhone via a Builder account or the same S3 backup.
---

Open **[builder.cdr.xyz](https://builder.cdr.xyz)**. Add to Home Screen on iPhone, or install as an app on a laptop. No npm, no GitHub Pages CORS.

![Web home: clock, open tasks, command bar](../../../assets/screenshots/web.png)

**Preferred:** create a builder.cdr.xyz account (email + password). Sign-in asks before it **overwrites** local data with the account snapshot. After that, sync **merges**: different tasks/notes keep both sides; the same item is last-write-wins on `updatedAt` / `completedAt` / `createdAt` (not a field-level CRDT). Deletes union. **Include AI credentials** (off by default) puts the phone API key in the snapshot for true `?` sync.

**Secondary:** the same five S3 fields as the phone. Credentials stay in this browser. They are posted only to the Builder Launcher Worker, which talks to your bucket (SigV4) at `builder-launcher/backup.enc`. Decrypt still happens in the browser. Last S3 upload wins.

## First load

1. Open [builder.cdr.xyz](https://builder.cdr.xyz).
2. Create an account or sign in. The gear on the home row (same height as headphones and weather) opens Settings (command bar stays in `>` — the slash list only opens if you type `/` or pick slash). Auto-sync defaults to **on save** (push shortly after an edit). **30s** / **5 min** also pull in the background. **off** is manual: **sync now**, `/pull`, `/push`. **pull** / **Sync now** returns as soon as the account snapshot is in — success does not wait on podcast RSS, quotes, or weather. Those fill in afterward (a hung feed cannot freeze the timestamp). Auth and vault calls time out after 20s instead of spinning forever.
3. On the phone: Settings → **… backup >**. Same email and password. **Create account** or **Sign in**, then **Sync now**.
4. Safari: Share → Add to Home Screen. Chrome/desktop: Install app.

S3 is still available under **S3 backup (optional)** if you already have a bucket.

The Worker origin is `https://builder.cdr.xyz` (also `https://builder-launcher.cdrxyz.workers.dev`).

## What it shows

Home matches the phone: analog clock, headphones and weather on the left, rotating ticker and a **gear** on the right (same row, headphones the same size as the cog), up to 3 open tasks, `… more tasks >`, and a command bar at the bottom. There is no top app bar on home — the marks sit at the top of the clock. Sync status sits under the clock where the phone shows the next calendar event. Other screens keep the gear in the header. The icon is a cog, not a sun. The **glyph on the left** holds the prefix (`>`, `-`, `+`, `$`, `/`). Calendar is phone-only. The field stays empty. Tap the glyph for the prefix menu. Type, then Enter / Go, or tap the **check** on the right to save a task (send mark in other modes) if the keyboard does not submit. On a phone, home stays pinned at the top with empty space above the bar — opening the keyboard shrinks that space instead of scrolling the clock off-screen. On an iPhone Home Screen install, extra space sits below the bar so it stays above the home indicator. Type `/` for slash commands (`notes`, `stocks`, `podcasts`, `weather`, `settings`, `tasks`, `pull`, `push`).

- **tasks** — full list. Tap a task to complete or reopen it. Pencil edits into the command bar. Done rows have an **X** to delete. Command bar starts in `-`.
- **notes** — listed by date edited. Command bar starts in `+`.
- **stocks** — watchlist. Command bar starts in `$`. Tap a row for the chart (1D / 1W / 1M / 3M / 1Y / 5Y). Drag across the chart to read price and date. Stats match the phone: open, high, low, volume, P/E, market cap, EPS, yield, beta, average volume, 52-week, CAGR.
- **podcasts** — recent, next episodes, subscriptions, and a **now playing** bar when an episode is loaded. Type a show name or RSS URL in `>` mode. Search uses Apple's catalog from this browser (it allows CORS). If that fails, the Worker searches fyyd.de. Subscribe fetches RSS through `/api/feed` because most feeds do not send CORS. Episode screen has a scrub bar, −15 / +15, and playback speed. Home shows headphones (tap for the list); play/pause while audio is loaded.
- **weather** — city in Settings (synced with the phone snapshot). Home shows the condition icon over the temperature, like the ticker. Tap it, or type `weather` / `/weather`, for the full forecast: large temperature and glyph, feels / precip / wind, next 24 hours, seven days with a short note, then humidity, dew point, UV, pressure, visibility, cloud cover, sunrise / sunset, wind, gusts, and air quality. Open-Meteo (plus air quality) via the Worker if the browser cannot fetch it.
- **settings** — Builder account first, **auto-sync** (off / on save / 30s / 5 min), weather location and units, **Include AI credentials**, S3 collapsed underneath. Sign-in and S3 save & pull ask before overwriting local data.

Offline, the last snapshot stays on the device. The browser only keeps a slim copy (subscriptions, playback progress, tickers, todos, notes) — episode catalogs stay in memory after RSS hydrate so Safari does not hit localStorage quota.

## Privacy

Account email is stored on Cloudflare (D1). The password is Argon2id. The snapshot JSON sits in R2 for that account (D1 keeps revision only). Account sync stores podcast subscriptions and in-progress playback, plus watchlist tickers — not episode bodies or quotes. RSS and Yahoo fill those in after sync. Session cookie is HttpOnly. S3 keys, when used, stay in `localStorage` on that browser. **forget S3** clears them. The Worker does not keep S3 keys. API keys inside a snapshot are not shown in the UI.

---
title: Web
description: Notes, tasks, stocks, and podcasts on laptop or iPhone via a Builder account or the same S3 backup.
---

Open **[builder.cdr.xyz](https://builder.cdr.xyz)**. Add to Home Screen on iPhone, or install as an app on a laptop. No npm, no GitHub Pages CORS.

![Web home: clock, open tasks, command bar](../../../assets/screenshots/web.png)

**Preferred:** create a builder.cdr.xyz account (email + password). Sign-in asks before it **overwrites** local data with the account snapshot. After that, sync merges. **Include AI credentials** (off by default) puts the phone API key in the snapshot for true `?` sync.

**Secondary:** the same five S3 fields as the phone. Credentials stay in this browser. They are posted only to the Builder Launcher Worker, which talks to your bucket (SigV4) at `builder-launcher/backup.enc`. Decrypt still happens in the browser. Last S3 upload wins.

## First load

1. Open [builder.cdr.xyz](https://builder.cdr.xyz).
2. Create an account or sign in. Sync runs on its own every 30 seconds while you are signed in.
3. On the phone: Settings → **… backup >**. Same email and password. **Create account** or **Sign in**, then **Sync now**.
4. Safari: Share → Add to Home Screen. Chrome/desktop: Install app.

S3 is still available under **S3 backup (optional)** if you already have a bucket.

The Worker origin is `https://builder.cdr.xyz` (also `https://builder-launcher.cdrxyz.workers.dev`).

## What it shows

Home matches the phone: analog clock, up to 3 open tasks, `… more tasks >`, and a command bar at the bottom. The **glyph on the left** holds the prefix (`>`, `-`, `+`, `$`, `/`). The field stays empty. Tap the glyph for the prefix menu. Type `/` for slash commands (`notes`, `stocks`, `podcasts`, `settings`, `tasks`, `pull`, `push`).

- **tasks** — full list. Command bar starts in `-`.
- **notes** — listed by date edited. Command bar starts in `+`.
- **stocks** — watchlist. Command bar starts in `$`. Tap a row for the chart (1D / 1W / 1M / 3M / 1Y / 5Y). Drag across the chart to read price and date. Stats match the phone: open, high, low, volume, P/E, market cap, EPS, yield, beta, average volume, 52-week, CAGR.
- **podcasts** — recent, next episodes, subscriptions. Type a show name or RSS URL in `>` mode. Search uses Apple's catalog from this browser (it allows CORS). If that fails, the Worker searches fyyd.de. Subscribe fetches RSS through `/api/feed` because most feeds do not send CORS. Episode screen has a scrub bar, −15 / +15, and playback speed.
- **settings** — Builder account first, **Include AI credentials**, S3 collapsed underneath. Sign-in and S3 save & pull ask before overwriting local data.

Offline, the last snapshot stays on the device.

## Privacy

Account email is stored on Cloudflare (D1). The password is Argon2id. The snapshot JSON sits in D1 for that account. Session cookie is HttpOnly. S3 keys, when used, stay in `localStorage` on that browser. **forget S3** clears them. The Worker does not keep S3 keys. API keys inside a snapshot are not shown in the UI.

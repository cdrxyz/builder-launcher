---
title: Privacy
description: What leaves the device, and what never does.
---

- **No analytics.**
- **Todos, notes, stocks, and podcasts** stay in local storage on the phone unless you turn on [Backup](backup/) (Builder account two-way sync, or an encrypted S3 snapshot you own), tap **Share unencrypted JSON**, or open the [web app](web/) with the same account or S3 credentials. The Pages host never sees those keys. A Builder account stores the snapshot in R2 for that login (email and password hash stay in D1). Account sync keeps podcast subscriptions and in-progress playback, plus watchlist tickers and order. Episode catalogs and live quotes are fetched after sync.
- **API keys and OAuth tokens** sit in encrypted SharedPreferences. They leave the device only as a Bearer token to the AI provider you chose, and only when you type `?`. The Hermes Web UI password is posted to `/api/auth/login` on your Web UI host (session cookie) and, when set, also sent as `Authorization: Bearer` on those requests. If Web UI auth fails, the same secret is sent as a Bearer token to the API server on `:8642`. OAuth tokens are never written into a backup. API keys stay out of Builder account sync, S3 backups, and the JSON share unless you turn on **Include AI credentials** (off by default).
- **S3 backup** is optional. Encrypted snapshot you upload after you set endpoint, bucket, keys, and an encryption key. Frequency `off` means manual only. Changing S3 credentials runs an access test against the bucket.
- **Builder account** stores email, Argon2id password hash, and session hashes in D1, and the snapshot JSON in R2, on the Worker that serves builder.cdr.xyz. Sign-in is rate-limited.
- **Weather** calls Open-Meteo with the coordinates of the city you picked (or a last GPS point if you never set a city and the OS already has a location).
- **Stocks** call Yahoo Finance for ticker search, quotes, charts, and fundamentals (P/E, yield, market cap, EPS, volume). The watchlist itself stays on the device. CSV copy/paste never leaves the phone.
- **SMS** is sent through the Android SMS APIs to the number you chose. Contacts are read only to complete `@` and `#`.
- **Calendar** is read on the device to show the next event under the home clock. Settings → **… calendar >** lists calendars and records which ones you include. Nothing is uploaded. `*` still opens the system calendar insert screen instead of writing events itself.
- **Hub** reads notifications you granted the listener for. It does not upload them.
- **Usage** reads Android usage stats you granted access for. Totals and your productive/distracting labels stay on the device.
- **Hermes** can be a cleartext LAN URL so a home box works. Do not point it at an untrusted network.

Settings footer: "Tokens stay on the device. They are sent only as a Bearer token to the provider you chose."

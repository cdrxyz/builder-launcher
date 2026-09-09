---
title: Privacy
description: What leaves the device, and what never does.
---

- **No analytics.**
- **Todos and notes** stay in local storage on the phone unless you turn on [Backup](backup/) (encrypted S3 snapshot you own) or tap **Share unencrypted JSON**.
- **API keys and OAuth tokens** sit in encrypted SharedPreferences. They leave the device only as a Bearer token to the AI provider you chose, and only when you type `?`. The Hermes Web UI password is posted to `/api/auth/login` on your Web UI host (session cookie) and, when set, also sent as `Authorization: Bearer` on those requests. If Web UI auth fails, the same secret is sent as a Bearer token to the API server on `:8642`. OAuth tokens are never written into a backup. An encrypted S3 snapshot may include the current provider API key. The unencrypted share file does not.
- **S3 backup** uploads only after you set endpoint, bucket, keys, and an encryption key. The object is encrypted on the phone. Frequency `off` means manual only.
- **Weather** calls Open-Meteo with the coordinates of the city you picked (or a last GPS point if you never set a city and the OS already has a location).
- **Stocks** call Yahoo Finance for ticker search, quotes, charts, and fundamentals (P/E, yield, market cap, EPS, volume). The watchlist itself stays on the device. CSV copy/paste never leaves the phone.
- **SMS** is sent through the Android SMS APIs to the number you chose. Contacts are read only to complete `@` and `#`.
- **Calendar** is read on the device to show the next event under the home clock. Nothing is uploaded. `*` still opens the system calendar insert screen instead of writing events itself.
- **Hub** reads notifications you granted the listener for. It does not upload them.
- **Usage** reads Android usage stats you granted access for. Totals and your productive/distracting labels stay on the device.
- **Hermes** can be a cleartext LAN URL so a home box works. Do not point it at an untrusted network.

Settings footer: "Tokens stay on the device. They are sent only as a Bearer token to the provider you chose."

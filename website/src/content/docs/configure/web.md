---
title: Web
description: Notes, tasks, stocks, and podcasts from the same encrypted S3 backup, hosted on Cloudflare.
---

Open **[builder.cdr.xyz](https://builder.cdr.xyz)**. Add to Home Screen on iPhone, or install as an app on a laptop. No npm, no GitHub Pages CORS.

![Web app tasks list from the S3 snapshot](../../../assets/screenshots/web.png)

It uses the **same S3 fields as the phone**: endpoint, bucket, access key, secret key, encryption key. Credentials stay in this browser. They are posted only to the Builder Launcher Worker, which talks to your bucket (SigV4) at `builder-launcher/backup.enc`. Decrypt still happens in the browser.

This is a snapshot, not two-way file sync. The last successful upload wins. Restore on the phone replaces local todos, notes, watchlist, and podcasts with that snapshot.

## First load

1. On the phone: Settings → **… backup >**. Fill S3 and encryption key. **Backup now**.
2. Open [builder.cdr.xyz](https://builder.cdr.xyz), paste the same five fields, **save & pull**.
3. Safari: Share → Add to Home Screen. Chrome/desktop: Install app.

The Worker origin is `https://builder.cdr.xyz` (also `https://builder-launcher.cdrxyz.workers.dev`). The bucket does **not** need CORS.

A static copy still ships on GitHub Pages at `/web/` if you want it. That copy talks to S3 from the browser and needs CORS; prefer the Cloudflare host.

## What it shows

- **tasks** — open items, then done. Tap to complete. Long-press / right-click deletes. Composer is `-` like the phone.
- **notes** — listed by date edited. Tap to read. Composer is `+`.
- **stocks** — the watchlist from the snapshot. Live Yahoo quotes through the Worker. Type `$AAPL` to add. Long-press / right-click removes.
- **pods** — recent unfinished plays, next episodes, then subscriptions A–Z. Tap an episode to play. Position is saved on this device and included in **push**. Search uses Apple's catalog via the Worker; paste an RSS URL or Overcast OPML.
- **pull** / **push** — download or replace `builder-launcher/backup.enc`. Push asks once. Chats, pins, and the rest of the snapshot ride along unchanged.
- **open file** — a `.enc` blob or the unencrypted JSON share.

Offline, the last pulled snapshot stays on the device. Pull again when you are back on the network.

## Privacy

S3 keys and the encryption key are stored in `localStorage` on that browser. **forget** clears them. The Worker does not keep the keys. API keys inside a snapshot are not shown in the UI.

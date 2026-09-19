---
title: Web
description: Notes, tasks, stocks, and podcasts from the same encrypted S3 backup on a laptop or iPhone.
---

Open [the web app](/builder-launcher/web/). Add to Home Screen on iPhone, or install as an app on a laptop.

![Web app tasks list from the S3 snapshot](../../../assets/screenshots/web.png)

It uses the **same S3 fields as the phone**: endpoint, bucket, access key, secret key, encryption key. Credentials stay in this browser. They are sent only to your bucket, as SigV4, to `builder-launcher/backup.enc`.

This is a snapshot, not two-way file sync. The last successful upload wins. Restore on the phone replaces local todos, notes, watchlist, and podcasts with that snapshot.

## First load

1. On the phone: Settings → **… backup >**. Fill S3 and encryption key. **Backup now**.
2. On the bucket, allow this origin (browsers require CORS; the Android app does not).
3. Open the web app, paste the same five fields, **save & pull**.

Safari / Chrome: Share → Add to Home Screen (iPhone) or Install app (desktop). After that it opens without browser chrome.

## CORS

R2 example. Bucket → Settings → CORS:

```json
[
  {
    "AllowedOrigins": [
      "https://cdrxyz.github.io"
    ],
    "AllowedMethods": ["GET", "PUT", "HEAD"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

Local docs preview also needs `http://localhost:4321`. A failed fetch with no HTTP status is almost always CORS, not a wrong secret.

Yahoo Finance and some RSS hosts also have to allow this origin for **live** quotes and feed refresh. The snapshot still shows last backup prices, subscriptions, episodes, and playback position if those calls are blocked.

## What it shows

- **tasks** — open items, then done. Tap to complete. Long-press / right-click deletes. Composer is `-` like the phone.
- **notes** — listed by date edited. Tap to read. Composer is `+`.
- **stocks** — the watchlist from the snapshot. Live quotes when Yahoo allows the browser; otherwise last backup price. Type `$AAPL` to add. Long-press / right-click removes.
- **pods** — recent unfinished plays, next episodes, then subscriptions A–Z, same order as the phone. Tap an episode to play the enclosure in the browser. Position is saved on this device and included in **push**. Search uses Apple's catalog; paste an RSS URL or Overcast OPML if search is blocked.
- **pull** / **push** — download or replace `builder-launcher/backup.enc`. Push asks once. Chats, pins, and the rest of the snapshot ride along unchanged.
- **open file** — a `.enc` blob or the unencrypted JSON share, if you would rather not talk to S3 from the browser.

Offline, the last pulled snapshot stays on the device. Pull again when you are back on the network.

## Privacy

S3 keys and the encryption key are stored in `localStorage` on that browser. **forget** clears them. The GitHub Pages host never sees the keys or the backup. API keys inside a snapshot are not shown in the UI.

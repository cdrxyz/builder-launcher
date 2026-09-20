---
title: Backup
description: Builder account two-way sync, optional encrypted S3, and unencrypted JSON share.
---

Type `settings` or `/settings`, then **… backup >**.

![Backup: S3 endpoint, keys, frequency, share](../../../assets/screenshots/backup.png)

## Builder account (preferred)

Create an account with email and password on the phone or at [builder.cdr.xyz](https://builder.cdr.xyz). Same login on both. **Create account** and **Sign in** show an alert: signing in **overwrites** local todos, notes, chats, pins, stocks, podcasts, alarms, and settings with the account snapshot. Cancel leaves this device alone. After you are signed in, **Sync now** merges both ways. Password is Argon2id. Account snapshots omit podcast episode HTML (the PWA refetches show notes from RSS). The JSON lives in R2 for that login, not in a 2 MB D1 row.

**Include AI credentials** is off by default. Turn it on in Settings → backup if you want the current provider API key in the account snapshot (true `?` sync across devices). OAuth tokens never go in.

## S3 (optional)

Use this only if you want your own bucket. Encrypted snapshot at `builder-launcher/backup.enc`. Last successful S3 upload wins. The web app still accepts the same five fields.

| Field | What it is |
| --- | --- |
| Endpoint | S3-compatible URL. R2 example: `https://ACCOUNT.r2.cloudflarestorage.com`. Also AWS, B2, MinIO. |
| Bucket | Bucket name. Object key is always `builder-launcher/backup.enc`. |
| Access key / Secret key | IAM or R2 API token. Stored in encrypted prefs on the phone. |
| Encryption key | Passphrase. The JSON is AES-GCM encrypted on the phone before upload. Lose it and the blob is unreadable. |
| Include AI credentials | Off by default. When on, the current provider API key is written into S3 backups, account snapshots, and the JSON share. OAuth tokens never go in. |
| Frequency | `off`, `daily`, or `weekly`. Daily/weekly run when you open the launcher if a backup is due. Account wins if you are signed in. |

Changing endpoint, bucket, access key, or secret key runs an S3 access test. `S3 access good` means the credentials can reach the bucket. A missing backup object is still a pass. If a backup is present, the test also tries your encryption key and reports `backup decrypts` or `Wrong encryption key`.

**Backup now** uploads to S3 when you are not signed in. **Restore from S3** asks once, then replaces local data. OAuth tokens and S3 credentials on this phone are left alone.

**Share unencrypted JSON** opens the Android share sheet with a plaintext dump (Signal, email, another program). API keys are omitted unless **Include AI credentials** is on.

Account and S3 are both optional. Leave them blank and nothing leaves the device.

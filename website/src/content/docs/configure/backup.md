---
title: Backup
description: Builder account two-way sync, optional encrypted S3, and unencrypted JSON share.
---

Type `settings` or `/settings`, then **… backup >**.

![Backup: S3 endpoint, keys, frequency, share](../../../assets/screenshots/backup.png)

## Builder account (preferred)

Create an account with email and password on the phone or at [builder.cdr.xyz](https://builder.cdr.xyz). Same login on both. The Worker stores one snapshot per account and **merges** todos, notes, chats, pins, watchlist, and podcasts by id when either side syncs. Password is Argon2id. Session token stays in encrypted prefs on the phone and in an HttpOnly cookie on the web app.

**Sync now** uploads and merges. **Restore from account** pulls the cloud snapshot, merges with what is on the phone, then writes back. Frequency `daily` / `weekly` uses the account when you are signed in.

OAuth tokens never go in. API keys stay out unless **Include AI credentials** is on.

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

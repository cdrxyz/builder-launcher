---
title: Backup
description: Encrypted S3 snapshot, restore, and unencrypted JSON share.
---

Type `settings` or `/settings`, then **… backup >**.

![Backup: S3 endpoint, keys, frequency, share](../../../assets/screenshots/backup.png)

This is a snapshot, not two-way sync. The last successful upload wins. Restore replaces what is on the phone.

| Field | What it is |
| --- | --- |
| Endpoint | S3-compatible URL. R2 example: `https://ACCOUNT.r2.cloudflarestorage.com`. Also AWS, B2, MinIO. |
| Bucket | Bucket name. Object key is always `builder-launcher/backup.enc`. |
| Access key / Secret key | IAM or R2 API token. Stored in encrypted prefs on the phone. |
| Encryption key | Passphrase. The JSON is AES-GCM encrypted on the phone before upload. Lose it and the blob is unreadable. |
| Include AI credentials | Off by default. When on, the current provider API key is written into S3 backups and the JSON share. Do not enable unless you use encrypted S3 backups or you understand the risk. OAuth tokens never go in. |
| Frequency | `off`, `daily`, or `weekly`. Daily/weekly run when you open the launcher if a backup is due. |

Changing endpoint, bucket, access key, or secret key runs an S3 access test (same idea as the AI provider status line). `S3 access good` means the credentials can reach the bucket. A missing backup object is still a pass. If a backup is present, the test also tries your encryption key and reports `backup decrypts` or `Wrong encryption key`.

**Backup now** uploads. **Restore from S3** asks once, then replaces todos, notes, chats, pins, watchlist, podcast subscriptions, alarms, world clocks, and settings. OAuth tokens and S3 credentials on this phone are left alone.

**Share unencrypted JSON** opens the Android share sheet with a plaintext dump (Signal, email, another program). API keys are omitted unless **Include AI credentials** is on.

S3 is optional. Leave the fields blank and nothing leaves the device.

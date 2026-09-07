# Builder Launcher

A simple Android launcher for builders who want to be deliberate with their phone. Open it, do the work, put it down, get back to life off screen.

The home screen is a command bar, not an icon grid. Inspired by Minimal OS on the Minimal Phone 2 (not affiliated with The Minimal Company). Works on ordinary slab phones and hardware-keyboard devices such as the Unihertz Titan 2 Elite.

AI stays optional and private: point `?` at your own Hermes instance, or paste a SuperGrok / xAI API key. Keys never leave the device except as a Bearer token to the URL you set.

## Install with Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium/releases).
2. Open this link on the phone (Obtainium must already be installed):

   https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/cdrxyz/builder-launcher

3. Confirm these fields if Obtainium asks:

   - App source URL: `https://github.com/cdrxyz/builder-launcher`
   - App ID: `xyz.cdr.builderlauncher`
   - APK filter: `builder-launcher-release`
   - Include prereleases: off (on only if you want APKs from open pull requests)

4. Add the app, install the APK, then set **Builder Launcher** as the default Home app.

Sideload without Obtainium: download `builder-launcher-release.apk` from [Releases](https://github.com/cdrxyz/builder-launcher/releases).

## Commands

Type on the home screen, then Enter.

| Prefix | Example | Action |
| --- | --- | --- |
| `@` | `@jason on my way!` | Open SMS to that contact or number |
| `#` | `#lauren` | Dial |
| `*` | `*dentist mar 24 9a` | Create a calendar event |
| `-` | `-buy milk` | Save a todo in the hub |
| `+` | `+ship notes` | Save a note in the hub |
| `?` | `?weather tomorrow` | Ask the configured LLM inline |
| (none) | `Termux` | Search and launch apps |
| | `hub` / `settings` / `help` | Built-ins |

D-pad right (or type `hub`) opens the hub: notifications you grant access to, plus local todos and notes. Tap the clock for settings.

## AI settings

Settings (tap the clock) → provider:

- **Hermes** — Base URL of your instance (OpenAI-compatible `/v1/chat/completions`). Example: `http://192.168.1.10:8642`. API key optional if the instance does not require one. Cleartext LAN URLs are allowed so a home box works.
- **xAI / SuperGrok** — `https://api.x.ai/v1` plus an API key from the xAI console. Default model `grok-4.6`.

Nothing is sent anywhere until you type `?`. No analytics.

## Keyboard phones

Keyboard mode: `auto` (default), `hardware`, or `software`. Auto treats a hardware QWERTY as present — Titan 2 Elite, Minimal Phone 2, and most BlackBerry-style Androids. The command bar sits next to the keys; the software keyboard stays out of the way.

## Releases and CI

Every pull request builds a signed APK and publishes a **prerelease** tagged `pr-<number>` (replaced on each push to that PR). Merges to `master` publish a latest GitHub Release so Obtainium can update.

## Build from source

JDK 17, Android SDK 35.

```bash
gradle :app:testDebugUnitTest :app:assembleRelease
```

Release signing uses PKCS12 env vars: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

## License

MIT. Built by [Cedar Labs](https://cdr.xyz).

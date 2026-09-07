# Minimos

Open-source Android launcher modeled on [Minimal OS](https://www.kickstarter.com/projects/minimalcompany/minimal-phone-2-the-modern-qwerty-phone) for the Minimal Phone 2: a TUI-like home screen, a command bar, and a single hub. It is a third-party clone, not affiliated with The Minimal Company.

Works on slab phones and hardware-keyboard devices (Unihertz Titan 2 Elite, Minimal Phone 2, and similar QWERTY Androids). On a physical keyboard the command bar sits at the bottom and the software IME stays out of the way.

AI stays on your terms: point `?` at a private Hermes instance, or paste a SuperGrok / xAI API key. Keys are stored in EncryptedSharedPreferences on the device.

## Install with Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium/releases).
2. Open this link on the phone (Obtainium must already be installed):

   https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/cdrxyz/minimos

3. Confirm these fields if Obtainium asks:

   - App source URL: `https://github.com/cdrxyz/minimos`
   - App ID: `xyz.cdr.minimos`
   - APK filter: `minimos-release`
   - Include prereleases: off (on only if you want APKs from open pull requests)

4. Add the app, install the APK, then set **Minimos** as the default Home app.

Sideload without Obtainium: download `minimos-release.apk` from [Releases](https://github.com/cdrxyz/minimos/releases).

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
| (none) | `Signal` | Search and launch apps |
| | `hub` / `settings` / `help` | Built-ins |

Swipe or press D-pad right for the hub (notifications you grant access to, plus local todos/notes). Tap the clock for settings.

## AI settings

Settings (tap the clock) → provider:

- **Hermes** — Base URL of your instance (OpenAI-compatible `/v1/chat/completions`). Example: `http://192.168.1.10:8642`. API key optional if the instance does not require one. Cleartext LAN URLs are allowed so a home Hermes box works.
- **xAI / SuperGrok** — `https://api.x.ai/v1` plus an API key from the xAI console (or a SuperGrok-compatible key). Default model `grok-4.6`.

Nothing is sent anywhere until you type `?`. No analytics.

## Keyboard phones

Keyboard mode: `auto` (default), `hardware`, or `software`. Auto treats `Configuration.KEYBOARD_QWERTY` as hardware — Titan 2 Elite, Minimal Phone 2, and most BlackBerry-style Androids.

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

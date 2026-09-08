# Builder Launcher

<p align="center">
  <img src="docs/logo/mark.svg" alt="Builder Launcher — B in a gear" width="120" />
</p>

A simple Android launcher for builders who want to be deliberate with their phone. Open it, do the work, put it down, get back to life off screen.

Manual: [Builder Launcher docs](https://cdrxyz.github.io/builder-launcher/) (Starlight on GitHub Pages). Source lives in `website/`.

The home screen is a command bar, not an icon grid. Works on ordinary slab phones and hardware-keyboard devices such as the Unihertz Titan 2 Elite.

AI stays optional and private: point `?` at your own Hermes instance, or sign in to xAI / OpenAI / Anthropic (API key still works as a fallback). Tokens never leave the device except as a Bearer token to the provider you chose.

<p align="center">
  <img src="docs/screenshots/home.png" alt="Home: clock, weather, todos, command bar" width="240" />
  <img src="docs/screenshots/hub.png" alt="Hub: messages you can reply to" width="240" />
  <img src="docs/screenshots/settings.png" alt="Settings: accent color, Hermes or xAI, keyboard mode" width="240" />
</p>

<p align="center">
  <img src="docs/screenshots/notes.png" alt="Notes list sorted by date edited" width="240" />
  <img src="docs/screenshots/note-editor.png" alt="Full-screen markdown note editor" width="240" />
  <img src="docs/screenshots/home-notes.png" alt="Typing notes shows … all notes >" width="240" />
  <img src="docs/screenshots/home-apps.png" alt="Typing filters apps and shows … all apps >" width="240" />
  <img src="docs/screenshots/all-apps.png" alt="All apps with icons, info, and delete" width="240" />
</p>

<p align="center">
  <img src="docs/screenshots/stocks.png" alt="Stocks watchlist with price and percent change" width="240" />
  <img src="docs/screenshots/stock-detail.png" alt="Ticker detail with chart, timeframes, and stats" width="240" />
  <img src="docs/screenshots/chat.png" alt="Full-screen AI chat with a markdown table answer" width="240" />
  <img src="docs/screenshots/chat-history.png" alt="Past AI conversations with dates and delete" width="240" />
</p>

| Home | Hub | Settings |
| --- | --- | --- |
| Clock, weather, last 3 todos, messages icon, command bar | Messages you can reply to | Hermes, xAI, OpenAI, Anthropic |

## Install with Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium/releases).
2. Open this link on the phone (Obtainium must already be installed):

   https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/cdrxyz/builder-launcher

3. Confirm these fields if Obtainium asks:

   - App source URL: `https://github.com/cdrxyz/builder-launcher`
   - App ID: `xyz.cdr.builderlauncher`
   - APK filter: `builder-launcher-release`
   - Include prereleases: off (on only if you want APKs from open pull requests)

4. Add the app, install the APK, then open **Builder Launcher**. Android will ask to set it as the default Home app (once). Settings → Set as default home app asks again if you declined.

Sideload without Obtainium: download `builder-launcher-release.apk` from [Releases](https://github.com/cdrxyz/builder-launcher/releases).

## Commands

Type on the home screen, then Enter.

| Prefix | Example | Action |
| --- | --- | --- |
| `@` | `@jason on my way!` | Draft an SMS in the launcher; Enter again to send |
| `#` | `#lauren` | Dial |
| `*` | `*dentist mar 24 9a` | Create a calendar event |
| `-` | `-buy milk` | Save a todo on home and the tasks list |
| `+` | `+` then write | Full-screen markdown note; first line starts as `# ` h1. `<` saves and goes home |
| | `notes` | Open all notes. App search shows `… all notes >` so it is not an installed Notes app |
| | `apps` | Open all installed apps. App search truncates and always ends with `… all apps >` |
| `$` | `$AAPL` or `$ apple` | Search and add a ticker to the stocks list |
| | `stocks` | Open all stocks. App search shows `… all stocks >` |
| `?` | `?` then write | Full-screen AI chat. Markdown answers (tables, lists, code). `<` home; history icon lists past chats with a date and delete |
| `/` | `/` then pick `settings` | Slash commands listed above the bar (apps, help, hub, notes, settings, stocks) |
| (none) | `Termux` | Search and launch apps |
| | `pin Termux` / `unpin Termux` | Pin or unpin on the home list |
| | `hub` / `notes` / `apps` / `stocks` / `settings` / `help` | Built-ins |

Home always shows the clock, current weather, and up to 3 open todos. Set the weather city and metric or imperial units in settings (autocomplete, no GPS). Tap a todo to strike it through; tap again to reopen it. `… more tasks >` is always on home and opens the full todos list (no clock; `<` returns home). The command bar there starts in `-` task mode. The copy icon copies open todos to the clipboard as a markdown checklist dated `YYYY-MM-DD` (no share sheet). Delete on the right permanently removes a task. Finished items sit below open ones, newest completed first. Type `+` to expand a full-screen markdown note. The first line is seeded with `# ` (`<` home, copy icon top right). Type `notes` (or tap `… all notes >` in the filtered app list) for notes sorted by date edited; first line is the title, delete on the right. Type `$` (or `stocks`, or tap `… all stocks >`) for the watchlist: ticker, company, price, and today's % change. Type `$AAPL` or `$ apple` to search and add. New tickers go to the top or bottom (settings → New stocks). Drag the grip on the left to reorder. Copy exports `Exchange,Ticker,Name` CSV; paste (or Enter a CSV in the bar) imports that format, an Apple Stocks `Symbol,Name,…` export, or one ticker per line. Tap a row for a chart with 1D / 1W / 1M / 3M / 1Y / 5Y and a stats box. Type an app name to filter a short, non-scrolling list that always ends with `… all apps >`. That opens every installed app (icons on the left; info for system app settings and delete to uninstall on the right). The command bar there filters the list as you type. The messages icon (top right), a swipe right, D-pad right, or `hub` opens the hub: SMS and chat threads you can answer. Reply (arrow on the right) sends through the notification when the app allows it; otherwise it opens the same screen as tapping the row (typical for Signal and Molly). Dismiss (x on the right) clears the notification. Todos stay on home and `… more tasks >`; notes stay on `notes`; stocks stay on `stocks`. Type `?` to open a full-screen AI chat (`<` home; history icon for past conversations with a date and delete). Hold an app on home to pin or unpin. `@` and `#` complete contacts as you type. Tap the clock for settings.

## AI settings

Settings (tap the clock) → provider:

- **Hermes** — Base URL of your instance (OpenAI-compatible `/v1/chat/completions`). Example: `http://192.168.1.10:8642`. API key optional if the instance does not require one. Cleartext LAN URLs are allowed so a home box works.
- **xAI** — Sign in with SuperGrok / X Premium+ (device-code OAuth at `auth.x.ai`) or paste an API key. Default model `grok-4.6`. Hits `https://api.x.ai/v1`.
- **OpenAI** — Sign in with ChatGPT (browser + paste the code or callback URL) or paste an API key. Default model `gpt-4o`. Hits `https://api.openai.com/v1`.
- **Anthropic** — Sign in with Claude (browser + paste the code) or paste an API key. Default model `claude-sonnet-4-5`. Hits `https://api.anthropic.com`.

OAuth tokens are stored in encrypted prefs on the device and refreshed automatically. Sign out from settings. An API key remains as a fallback if OAuth is unavailable for your plan.

Nothing is sent anywhere until you type `?`. No analytics.

## Keyboard phones

Keyboard mode: `auto` (default), `hardware`, or `software`. Auto treats a hardware QWERTY as present — Titan 2 Elite and most BlackBerry-style Androids. The command bar always sits at the bottom, just above the keyboard. On hardware-keyboard phones the software keyboard stays out of the way.

## Releases and CI

Every pull request builds a signed APK and publishes a **prerelease** tagged `pr-<number>` (replaced on each push to that PR). Merges to `master` publish a latest GitHub Release so Obtainium can update.

## Build from source

JDK 17 and Gradle 8.11.1 are pinned with Hermit (`bin/`). Android SDK 35.

```bash
source bin/activate-hermit
gradle :app:testDebugUnitTest :app:assembleRelease
```

Release signing uses PKCS12 env vars: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

## License

MIT. Built by [Cedar Labs](https://cdr.xyz).

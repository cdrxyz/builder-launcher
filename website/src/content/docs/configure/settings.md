---
title: Settings
description: Type settings. AI providers, keyboard, clock face, weather, clock sound, calendar, backup, hub and usage access, default Home.
---

Type `settings` or `/settings`.

![Settings: accent, AI providers link, keyboard, weather](../../../assets/screenshots/settings.png)

| Block | What to set |
| --- | --- |
| Accent | Color chips for the cursor, `>` prompt, selected chips, and the `>` on links like more tasks. Default cyberpunk green. |
| AI providers | `… AI providers >` opens the dedicated screen. [Details](ai-providers/). |
| Keyboard | `auto`, `hardware`, or `software`. [Keyboard](keyboard/). |
| Home apps | `plaintext` (default) or `icons`. Names vs grayscale icons for pins and home search. All apps stays in color. **Pin time** `off` (default) or `on` shows minutes today and share of phone time. Names: `30m (17%)` beside the pin. Icons: `30m` then `17%` under the icon. Green if the app is productive, red if not. Needs [usage](../use/usage/) access. |
| Clock face | `analog` (default) or `digital`. Analog sits in the center of home with the time and date below. |
| Weather location | Type a city, pick a match. Placeholder: New York. No GPS required. |
| Weather units | `metric` (Celsius) or `imperial` (Fahrenheit). |
| Clock sound | `pulse`, `chime`, `bell`, `orthodox`, `hum`, or `off`. Tap a name to hear it. These are Android Open Source Project ringtones (Apache 2.0). Alerts play on the alarm stream through silent and vibrate. |
| Calendar | `… calendar >` opens the dedicated screen. Shows whether calendar access is granted and which calendars feed the next event on home. [Details](calendar/). |
| Backup | `… backup >` opens the dedicated screen. S3 snapshot, restore, and JSON share. [Details](backup/). |
| Notification access (hub) | Opens Android's notification listener settings. |
| Usage access | Opens Android's usage-access settings so [usage](../use/usage/) can read screen time. |
| Set as default home app | Asks Android again if you declined the first prompt. |

Footer: tokens stay on the device and are sent only as a Bearer token to the provider you chose.

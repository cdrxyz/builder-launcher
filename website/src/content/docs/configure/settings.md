---
title: Settings
description: Type settings. AI, keyboard, clock face, weather, clock sound, hub and usage access, default Home.
---

Type `settings` or `/settings`.

![Settings: AI provider, keyboard, weather city and units](../../../assets/screenshots/settings.png)

| Block | What to set |
| --- | --- |
| AI provider | Hermes, xAI, OpenAI, Anthropic. [Details](ai-providers/). |
| Hermes base URL | Only for Hermes. Example `http://192.168.1.10:8642`. |
| Sign in / API key | xAI SuperGrok device login, or paste a key. Keys stay on device. |
| Model | Blank uses the provider default. |
| Accent | Color chips for the cursor, `>` prompt, selected chips, and the `>` on links like more tasks. Default cyberpunk green. |
| Keyboard | `auto`, `hardware`, or `software`. [Keyboard](keyboard/). |
| Home apps | `plaintext` (default) or `icons`. Names vs grayscale icons for pins and home search. All apps stays in color. |
| Clock face | `analog` (default) or `digital`. Analog sits in the center of home with the time and date below. |
| Weather location | Type a city, pick a match. Placeholder: New York. No GPS required. |
| Weather units | `metric` (Celsius) or `imperial` (Fahrenheit). |
| Clock sound | `pulse`, `chime`, `bell`, `orthodox`, `hum`, or `off`. Tap a name to hear it. Each tone is a 10-30s loop. Alarms fade in over 4 seconds. |
| Notification access (hub) | Opens Android's notification listener settings. |
| Usage access | Opens Android's usage-access settings so [usage](../use/usage/) can read screen time. |
| Set as default home app | Asks Android again if you declined the first prompt. |

Footer: tokens stay on the device and are sent only as a Bearer token to the provider you chose.

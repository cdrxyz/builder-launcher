---
title: Commands
description: Prefix commands and built-ins for Builder Launcher.
---

Type on home, then Enter. Empty prefixes (`@` with no name, `pin` with no app) show help.

| Prefix | Example | Action |
| --- | --- | --- |
| `@` | `@jason on my way!` | Draft an SMS in the launcher; Enter again to send |
| `#` | `#lauren` | Dial |
| `*` | `*dentist mar 24 9a` | Create a calendar event |
| `-` | `-buy milk` | Save a todo on home and the tasks list |
| `+` | `+` then write | Full-screen markdown note; first line starts as `# ` h1. `<` saves and goes home |
| `$` | `$AAPL` or `$ apple` | Search and add a ticker to the [stocks](stocks/) list |
| | `stocks` | Open all stocks. App search shows `… all stocks >` |
| `?` | `?` then write | Full-screen AI [chat](ai/). Markdown answers. `<` home; provider mark opens Grok/ChatGPT/Claude/Hermes with the prompt; history lists past chats with a date and delete |
| (none) | `Termux` | Search and launch apps |
| | `pin Termux` / `unpin Termux` | Pin or unpin. Pins show as icons on home |
| | `hub` / `notes` / `apps` / `stocks` / `clock` / `weather` / `settings` / `help` | Built-ins |
| | `timer` | Open the clock app's timer if no app named timer matches |

`help` and `/help` show the on-screen cheat sheet. A lone `?` opens [Ask AI](ai/).

`settings` and `/settings` open settings. Same for `hub`, `notes`, `apps`, `stocks`, `clock`, and `weather`.

## Where each thing lives

- Todos stay on [home](home/) and `… more tasks >`.
- Notes stay on [notes](notes/).
- Stocks stay on [stocks](stocks/).
- Clock stays on [clock](clock/).
- Weather stays on [weather](weather/).
- AI chats stay on [Ask AI](ai/).
- Notifications stay on the [hub](hub/).
- Installed apps stay on [apps](apps/).

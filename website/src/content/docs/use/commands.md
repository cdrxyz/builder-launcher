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
| `?` | `?weather tomorrow` | Ask the configured LLM inline |
| (none) | `Termux` | Search and launch apps |
| | `pin Termux` / `unpin Termux` | Pin or unpin on the home list |
| | `hub` / `notes` / `apps` / `settings` / `help` | Built-ins |
| | `timer` | Open the clock app's timer if no app named timer matches |

`help`, `/help`, and a lone `?` show the on-screen cheat sheet.

`settings` and `/settings` open settings. Same for `hub`, `notes`, and `apps`.

## Where each thing lives

- Todos stay on [home](home/) and `… more tasks >`.
- Notes stay on [notes](notes/).
- Notifications stay on the [hub](hub/).
- Installed apps stay on [apps](apps/).

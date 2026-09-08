---
title: Screenshots
description: Paparazzi goldens, README PNGs, and docs-site assets.
---

UI regressions are locked with Paparazzi (`LauncherScreenshotTest`). After a visible change:

```bash
source bin/activate-hermit
gradle :app:recordPaparazziDebug
```

Commit the updated files under `app/src/test/snapshots/`. CI runs `verifyPaparazziDebug`.

Copy a golden into `docs/screenshots/` when the README should match, **and** into `website/src/assets/screenshots/` when this docs site should match.

```bash
python3 docs/screenshots/render.py
```

is the offline fallback when you cannot run Gradle. Do not commit `*-ai.png` drafts.

| README / docs PNG | Typical Paparazzi test |
| --- | --- |
| `home.png` | `home` |
| `home-ticker.png` | `homeTicker` |
| `todos.png` | `todos` |
| `commands.png` / `command-menu.png` | `homeCommandMenu` |
| `notes.png` | `notes` |
| `note-editor.png` | `noteEditor` |
| `home-notes.png` | `homeNotesShortcut` |
| `home-apps.png` | `homeAppsFilter` |
| `home-pinned.png` | `homePinned` |
| `all-apps.png` | `allApps` |
| `home-stocks.png` | `homeStocksShortcut` |
| `stocks.png` | `stocks` |
| `stocks-settings.png` | `stocksSettings` |
| `stock-detail.png` | `stockDetail` |
| `hub.png` | `hub` |
| `settings.png` | `settings` |
| `clock.png` | `clock` |
| `weather.png` | `weather` |

PRs that change UI must include screenshots in the PR body (see `.github/pull_request_template.md`).

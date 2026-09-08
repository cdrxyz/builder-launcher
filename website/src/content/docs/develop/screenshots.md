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
| `todos.png` | `todos` |
| `commands.png` / `command-menu.png` | `homeCommandMenu` |
| `notes.png` | `notes` |
| `note-editor.png` | `noteEditor` |
| `home-notes.png` | `homeNotesShortcut` |
| `home-apps.png` | `homeAppsFilter` |
| `all-apps.png` | `allApps` |
| `hub.png` | `hub` |
| `settings.png` | `settings` |

PRs that change UI must include screenshots in the PR body (see `.github/pull_request_template.md`).

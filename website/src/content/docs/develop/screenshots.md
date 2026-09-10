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
| `home-playing.png` | `homePlaying` |
| `home-timer.png` | `homeTimer` |
| `home-ticker.png` | `homeTicker` |
| `home-todo-wrap.png` | `homeTodoWrap` |
| `home-todo-single-line.png` | `homeTodoSingleLine` |
| `todos.png` | `todos` |
| `todos-edit.png` | `todosEdit` |
| `commands.png` / `command-menu.png` | `homeCommandMenu` |
| `notes.png` | `notes` |
| `note-editor.png` | `noteEditor` |
| `home-notes.png` | `homeNotesShortcut` |
| `home-apps.png` | `homeAppsFilter` |
| `home-pinned.png` | `homePinned` |
| `home-pinned-icons.png` | `homePinnedIcons` |
| `home-pinned-usage.png` | `homePinnedUsage` |
| `home-pinned-icons-usage.png` | `homePinnedIconsUsage` |
| `all-apps.png` | `allApps` |
| `home-stocks.png` | `homeStocksShortcut` |
| `home-podcasts.png` | `homePodcastsShortcut` |
| `podcasts.png` | `podcasts` |
| `podcasts-search.png` | `podcastsSearch` |
| `podcasts-settings.png` | `podcastsSettings` |
| `podcast-show.png` | `podcastShow` |
| `podcast-episode.png` | `podcastEpisode` |
| `podcasts-theme-plain.png` | `podcastsThemePlain` |
| `podcasts-theme-material.png` | `podcastsThemeMaterial` |
| `podcasts-theme-ios.png` | `podcastsThemeIos` |
| `stocks.png` | `stocks` |
| `stocks-theme-plain.png` | `stocksThemePlain` |
| `stocks-theme-material.png` | `stocksThemeMaterial` |
| `stocks-theme-ios.png` | `stocksThemeIos` |
| `stocks-settings.png` | `stocksSettings` |
| `stock-detail.png` | `stockDetail` |
| `hub.png` | `hub` |
| `hub-reply.png` | `hubReply` |
| `settings.png` | `settings` |
| `settings-theme-plain.png` | `settingsThemePlain` |
| `settings-theme-material.png` | `settingsThemeMaterial` |
| `settings-theme-ios.png` | `settingsThemeIos` |
| `calendar-settings.png` | `settingsCalendar` |
| `backup.png` | `settingsBackup` |
| `ai-providers.png` | `aiProviders` |
| `clock.png` | `clock` |
| `clock-timer-alert.png` | `clockTimerAlert` |
| `clock-alarm-alert.png` | `clockAlarmAlert` |
| `weather.png` | `weather` |
| `usage.png` | `usage` |
| `usage-theme-plain.png` | `usageThemePlain` |
| `usage-theme-material.png` | `usageThemeMaterial` |
| `usage-theme-ios.png` | `usageThemeIos` |
| `usage-scrub.png` | `usageScrub` |
| `home-theme-plain.png` | `homeThemePlain` |
| `home-theme-material.png` | `homeThemeMaterial` |
| `home-theme-ios.png` | `homeThemeIos` |

Theme comparison lives on [Themes](../configure/themes/).

PRs that change UI must include screenshots in the PR body (see `.github/pull_request_template.md`).

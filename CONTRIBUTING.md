# Contributing

## Pull requests

- Branch: `YYYY-MM-DD.eizus.description` in a dedicated worktree. Do not feature-checkout the main clone.
- Reviewer: `@adrw`.
- UI changes (layout, on-screen copy, theme, navigation, command bar, hub, settings) **must** include screenshots in the PR body. Use the template in `.github/pull_request_template.md`. Prefer Paparazzi goldens plus README PNGs under `docs/screenshots/`.
- If there is no UI change, write `n/a` under Screenshots.
- Do not commit keystores, API keys, or `*-ai.png` drafts.

## Screenshots

UI regressions are locked with Paparazzi (`LauncherScreenshotTest`). After a visible change:

```bash
gradle :app:recordPaparazziDebug
```

Commit the updated files under `app/src/test/snapshots/`. CI runs `verifyPaparazziDebug` (after goldens land). Copy a golden into `docs/screenshots/` when the README should match.

```bash
python3 docs/screenshots/render.py
```

is the offline fallback when you cannot run Gradle.

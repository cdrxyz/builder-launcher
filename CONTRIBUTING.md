# Contributing

## Pull requests

- Branch: `YYYY-MM-DD.eizus.description` in a dedicated worktree. Do not feature-checkout the main clone.
- Reviewer: `@adrw`.
- UI changes (layout, on-screen copy, theme, navigation, command bar, hub, settings) **must** include screenshots in the PR body. Use the template in `.github/pull_request_template.md`. Prefer PNGs under `docs/screenshots/` so the README stays in sync.
- If there is no UI change, write `n/a` under Screenshots.
- Do not commit keystores, API keys, or `*-ai.png` drafts.

## Screenshots

Regenerate the README captures after a visible UI change:

```bash
python3 docs/screenshots/render.py
```

Replace `home.png` / `hub.png` / `settings.png` with device captures when a real APK screenshot is better.

---
title: Keep docs in sync
description: Every feature PR updates Starlight docs for that feature, or adds a page if it is new.
---

This site is the human manual. Feature PRs that skip docs are incomplete.

## Required on every feature PR

| Change | Also update |
| --- | --- |
| Existing feature changed | Matching page under `website/src/content/docs/` (steps, tables, screenshots) |
| Net-new feature | New page, sidebar entry in `website/astro.config.mjs`, screenshot in `website/src/assets/screenshots/`, link from [Commands](../use/commands/) or [Settings](../configure/settings/) if users reach it that way |
| Feature removed | Delete or rewrite the page and drop the sidebar entry |
| UI copy or layout | Paparazzi goldens, `docs/screenshots/`, and docs assets. See [Screenshots](screenshots/) |
| New prefix / built-in | [Commands](../use/commands/) **and** the feature page **and** on-screen help |
| New settings field | [Settings](../configure/settings/) plus the topic page |
| README-only fact | README **and** the matching docs page — they must not disagree |

Check the Documentation section in `.github/pull_request_template.md`. Reviewers reject feature PRs that skip this.

CI builds `website/` on every pull request (`npm ci && npm run build` in `.github/workflows/docs.yml`). A green docs build only proves Starlight compiles.

## Local preview

```bash
cd website
npm ci
npm run dev
```

GitHub Pages URL after merge to `master`: https://cdrxyz.github.io/builder-launcher/

## Sidebar map

Keep `website/astro.config.mjs` `sidebar` in lockstep with files on disk. Page slug is the path under `src/content/docs/` without the extension (`use/notes.md` → `use/notes`).

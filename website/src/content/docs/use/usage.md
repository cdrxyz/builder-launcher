---
title: Usage
description: Screen time on the phone. Productive vs distracting, today / 1M / 3M / 6M, most-used apps.
---

![Usage with today's hourly chart and most-used apps](../../../assets/screenshots/usage.png)

![Press a bar to see that hour](../../../assets/screenshots/usage-scrub.png)

Type `usage` or `/usage`, tap the descending bar chart at the top left of [home](home/), swipe from the left of home, or press D-pad left.

Android keeps the totals. Builder Launcher does not upload them. Grant **Usage access** the first time (also in [settings](../../configure/settings/)).

| Piece | What it does |
| --- | --- |
| today / 1M / 3M / 6M | Stock-style ranges. **today** is 24 hours. **1M** is days. **3M** and **6M** are weeks. |
| Total | Time in the foreground for the selected range, or for the pressed bar. |
| vs yesterday | Shown for **today** until you press a bar. |
| Pickups | Screen-on events for the selected range. |
| Chart | Stacked bars: productive (accent), distracting (red), other (dim). Press a bar to read that hour, day, or week. |
| Breakdown | Share of productive / distracting / other. |
| Most used | Top apps in the range. Tap a row to cycle **other → productive → distracting**. Your choice sticks on the device. |

YouTube, Instagram, and similar apps start as distracting. Termux, Slack, Gmail, calendars, and maps start as productive. Everything else starts as other, including this launcher and browsers.

`usage` sits top left in the accent color. Swipe left, `>` (top right), or Back returns home.

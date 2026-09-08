---
title: Usage
description: Screen time on the phone. Hourly today, 7 days, 30 days, 6 months. Drag a bar for that hour or day.
---

![Usage with today's hourly chart and most-used apps](../../../assets/screenshots/usage.png)

![Drag a bar to see that hour](../../../assets/screenshots/usage-scrub.png)

Type `usage` or `/usage`, tap the descending bar chart at the top left of [home](home/), pull from the left of home, or press D-pad left.

Android keeps the totals. Builder Launcher does not upload them. Grant **Usage access** the first time (also in [settings](../../configure/settings/)).

| Piece | What it does |
| --- | --- |
| today / 7D / 30D / 6M | Same chips as [stocks](stocks/). **today** is 24 hourly bars. **7D** and **30D** are days. **6M** is weeks. |
| Total | Foreground time for the selected range. Drag a bar and the total, date, and breakdown switch to that hour, day, or week. |
| vs yesterday | Shown for **today** until you drag a bar. |
| Pickups | Screen-on events for the range (hidden while dragging). |
| Chart | Stacked bars: productive (accent), distracting (red), other (dim). Drag across like a ticker chart. |
| Breakdown | Share of productive / distracting / other for the range, or the selected bar. |
| Most used | Top apps for the whole range. Tap a row to cycle **other → productive → distracting**. Your choice sticks on the device. |

YouTube, Instagram, and similar apps start as distracting. Termux, Slack, Gmail, calendars, and maps start as productive. Everything else starts as other, including this launcher and browsers.

Swipe left, `<`, or Back returns home.

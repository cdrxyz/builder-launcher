---
title: Home
description: Clock, weather, rotating watchlist ticker, three open todos, pinned app icons, and the command bar.
---

![Home with clock, weather, last three todos, and the command bar](../../../assets/screenshots/home.png)

![Home with a running timer in place of the clock](../../../assets/screenshots/home-timer.png)

![Home with a watchlist ticker between the clock and the hub icon](../../../assets/screenshots/home-ticker.png)

Home is empty on purpose. There is no icon grid. Apps you pin sit in a compact row of icons.

| Piece | What it does |
| --- | --- |
| Clock | Large time, weekday, date. While a timer is running, the time is the countdown instead. Tap it for [clock](clock/) (timer, alarm, time zones). |
| Ticker | To the right of the clock, between the time and the hub icon. Cycles the [watchlist](stocks/) every 5 seconds with today's percent (green up, red down). Hidden when the list is empty. Tap it for that ticker's chart. |
| Weather | Current condition under the date. Tap it for the [weather](weather/) forecast. [Configure weather](../configure/weather/). |
| Open todos | Up to 3 open tasks. Tap to strike through; tap again to reopen. |
| `… more tasks >` | Always on home. Opens the full tasks list (no clock). `<` returns home. |
| Pinned apps | Horizontal row of icons. Tap to launch. Hold and drag to reorder. See [apps](apps/). |
| Command bar | Bottom of the screen. Type, then Enter. |

Finished todos do **not** sit on home. They live on the tasks list under open items, newest completed first.

## Tasks list

![Full todos list with more-tasks command bar in dash mode](../../../assets/screenshots/todos.png)

On the tasks list the command bar starts in `-` task mode. The copy icon copies open todos to the clipboard as a markdown checklist dated `YYYY-MM-DD` (no share sheet).

See [Todos](todos/) for the `-` command.

---
title: Home
description: Analog clock, next calendar event, weather on the left, usage, rotating watchlist ticker, three open todos, pinned apps, and the command bar.
---

![Home with clock, next calendar event, weather, last three todos, and the command bar](../../../assets/screenshots/home.png)

![Home with a running timer in place of the clock](../../../assets/screenshots/home-timer.png)

![Home with weather on the left and the watchlist ticker on the right](../../../assets/screenshots/home-ticker.png)

Home is empty on purpose. There is no icon grid. Apps you pin sit as names, or as a compact row of icons if you turn that on in [settings](../configure/settings/).

| Piece | What it does |
| --- | --- |
| Usage | Descending bar-chart mark, top left. Tap it for [usage](usage/) (screen time). Swipe from the left of home, or D-pad left, also opens it. The whole home screen slides with your finger. |
| Weather | Condition icon over the temperature, on the left of the clock. Tap it for the [weather](weather/) forecast. [Configure weather](../configure/weather/). |
| Clock | Centered. Analog face by default, with digital time and weekday/date below. Settings can switch to digital only. While a timer is running, the large time is the countdown instead. Tap it for [clock](clock/) (timer, alarm, time zones). |
| Next event | Under the date. Title and time from the device calendar (`dentist · 09:00`, or `dentist · Tue 09:00` if it is not today). All-day events say `today` or the weekday. Hidden until you grant calendar access, and while nothing is upcoming. Tap it to open that event. See [Calendar](calendar/). |
| Ticker | To the right of the clock, left of the hub icon. Cycles the [watchlist](stocks/) every 5 seconds with today's percent (green up, red down). Hidden when the list is empty. Tap it for that ticker's chart. |
| Hub | Messages icon, top right. Tap, swipe from the right of home, or D-pad right opens the [hub](hub/). |
| Open todos | Up to 3 open tasks. Tap to strike through; tap again to reopen. |
| `… more tasks >` | Always on home. Opens the full tasks list (no clock). `<` returns home. |
| Pinned apps | Names by default. Tap to launch. Hold and drag to reorder. [Settings](../configure/settings/) can switch to a centered icon row. See [apps](apps/). |
| Command bar | Bottom of the screen. Type, then Enter. |

Finished todos do **not** sit on home. They live on the tasks list under open items, newest completed first.

Swipe up to Home (or the Home button) while already in Builder Launcher returns to this home screen — tasks, hub, notes, settings, and the rest. Home from another app restores the last launcher page instead: leave from tasks, come back on tasks.

Back is not Home. From tasks, hub, notes, and the rest it returns to this home screen. On home it resets the command bar to normal `>` mode if a prefix is active. It does not finish the launcher or reload the page you were on.

## Tasks list

![Full todos list with more-tasks command bar in dash mode](../../../assets/screenshots/todos.png)

On the tasks list the command bar starts in `-` task mode. Long-press and drag an open row to reorder (saved on the device; home preview stays tap-only). Pencil (before delete) loads that task into the bar to edit. The copy icon copies open todos to the clipboard as a markdown checklist dated `YYYY-MM-DD` (no share sheet).

See [Todos](todos/) for the `-` command.

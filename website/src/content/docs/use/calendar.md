---
title: Calendar
description: Create a calendar event with *title and a time.
---

Prefix `*`.

Examples:

| You type | Result |
| --- | --- |
| `*dentist mar 24 9a` | Event titled dentist, start parsed as 24 Mar 9:00 |
| `*standup tomorrow 9:30` | Tomorrow at 9:30 |
| `*ship tonight` | Tonight (default 20:00 if no clock) |
| `*idea dump` | Title only; calendar app opens without a start time |

Recognized when-text: month + day (`mar 24`), `today` / `tomorrow` / `tonight`, and clocks like `9a`, `9am`, `9:30`, `21:00`. A past month/day rolls to next year. A clock-only time that already passed today rolls to tomorrow.

The system calendar app receives `ACTION_INSERT`. If none is installed you get "No calendar app".

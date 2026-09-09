---
title: Calendar
description: Next event under the home clock, and create events with *title and a time.
---

Home shows the next event from the calendars on the phone, under the date:

| Kind | Line |
| --- | --- |
| Timed, today | `dentist · 09:00` |
| Timed, later | `dentist · Tue 09:00` |
| All-day, today | `dentist · today` |
| All-day, later | `dentist · Tue` |

Android asks for calendar access once, with contacts. Deny it and the line stays hidden — there is no prompt on home. Grant it and the next event (or the meeting you are already in) appears. Recurring meetings are included. Tap the line to open that event in the calendar app.

`*` still creates events. It does not write the calendar itself.

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

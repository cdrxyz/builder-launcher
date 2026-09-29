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

Android asks for calendar access once, with contacts. Deny it and the line stays hidden — there is no prompt on home. Grant it from **settings → … calendar >** (or Android app permissions) and the next event (or the meeting you are already in) appears. Recurring meetings are included. Tap the line to open that event in the calendar app.

Pick calendars on that same settings screen. Unchecked calendars stay off home even if they are visible in the system calendar app. Hidden system calendars stay off until you check them there. The screen also shows whether access is granted and which event home will highlight.

`*` still creates events. It does not write the calendar itself.

Prefix `*`.

Examples:

| You type | Result |
| --- | --- |
| `*dentist mar 24 9a` | Event titled dentist, start parsed as 24 Mar 9:00 |
| `*dentist friday 9a` | Next Friday at 9:00. `next monday` on a Monday skips a week |
| `*standup 9/28 9:30` | 28 Sep at 9:30. Month/day, not day/month |
| `*ship 2026-10-01` | 1 Oct 2026 at 9:00. An explicit year stays put |
| `*standup tomorrow 9:30` | Tomorrow at 9:30 |
| `*ship tonight` | Tonight (default 20:00 if no clock) |
| `*idea dump` | Title only; calendar app opens without a start time |

Recognized when-text: month + day (`mar 24`, `mar 24 2027`), numeric month/day (`9/28`, `9/28/26`), ISO dates (`2026-09-28`), weekdays (`friday`, `mon`, `this fri`, `next monday`), `today` / `tomorrow` / `tonight`, and clocks like `9a`, `9am`, `9:30`, `21:00`. A range (`9a-10:30`) sets the end. Otherwise the event is one hour. A past month/day or numeric date with no year rolls to next year. A weekday uses the next matching day, or a week later if that clock already passed today. `next monday` on a Monday skips to the following Monday. A clock-only time that already passed today rolls to tomorrow.

The system calendar app receives `ACTION_INSERT` with the start and end. If none is installed you get "No calendar app".

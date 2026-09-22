---
title: Todos
description: Save tasks with -, preview them on home, expand the full list.
---

Prefix `-`. Example: `-buy milk`. From default `>` mode, typing with no app matches switches to this mode after three more characters, unless the text looks like math or a ticker.

![Three open todos on home](../../../assets/screenshots/home.png)

- Saved locally on the device (not in a cloud todo app).
- Long tasks wrap in the bar on home and on the tasks page after they no longer fit on one line. Enter still saves.

![A long task wrapping in the home command bar](../../../assets/screenshots/home-todo-wrap.png)
- Home shows **open** todos, up to the count in [settings](../configure/settings/) (default 3). `0` hides them on home.
- Tap a line on home to complete it. The line strikes through, then fades after a second and moves to the top of completed on the tasks page. On the tasks page, the checkbox completes or reopens; tap a done line to reopen it.
- `… more tasks >` is always on home and opens the full list.

![Full tasks page with open and completed items](../../../assets/screenshots/todos.png)

![Editing a task from the tasks page](../../../assets/screenshots/todos-edit.png)

On the tasks page:

- Title `tasks` is centered at the top. Copy stays top right.

- Command bar starts with `-` so the next Enter saves another todo.
- A checkbox on the left completes or reopens a task. Checking strikes through the line, waits a second, then fades it into the top of the completed list. Tap the text of an open task to load it into the bar. A check on the right of the bar saves the edit. Delete is only on completed tasks.
- `<` or Back returns home in default command-bar mode (not task mode).
- Long-press and drag an open row across the list to reorder. On the web app, up and down arrows do the same, and a long-press drag works there too. A new task lands at the top on both the phone and the web, including after sync, until you move that task lower. The position syncs with the account. Home preview stays tap-only.
- Copy icon writes open todos to the clipboard as:

  ```markdown
  ## 2026-09-07
  - [ ] buy milk
  ```

Completed items sort newest-finished first and stay off home.

---
title: Default home app
description: Android asks once. Settings can ask again if you declined.
---

Builder Launcher is a Home app (`ROLE_HOME`).

- On first resume after contacts permission, Android is asked **once** to make it the default Home.
- If you already hold the role, it does not ask again.
- If you declined, Settings → **Set as default home app** asks again.

Until it is default Home, the system launcher still owns the Home button. Install still works; you just open Builder Launcher like any other app.

Once it is default Home: swipe up to Home while already in the launcher goes to the home screen. Swipe up to Home from another app restores the last launcher page (for example tasks). The Back button returns to the home screen from an inner page and on home resets the command bar to `>` if a prefix is active; it does not reload the current page.

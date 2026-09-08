---
title: Privacy
description: What leaves the device, and what never does.
---

- **No analytics.**
- **Todos and notes** stay in local storage on the phone.
- **API keys and OAuth tokens** sit in encrypted SharedPreferences. They leave the device only as a Bearer token to the AI provider you chose, and only when you type `?`.
- **Weather** calls Open-Meteo with the coordinates of the city you picked (or a last GPS point if you never set a city and the OS already has a location).
- **SMS** is sent through the Android SMS APIs to the number you chose. Contacts are read only to complete `@` and `#`.
- **Hub** reads notifications you granted the listener for. It does not upload them.
- **Hermes** can be a cleartext LAN URL so a home box works. Do not point it at an untrusted network.

Settings footer: "Tokens stay on the device. They are sent only as a Bearer token to the provider you chose."

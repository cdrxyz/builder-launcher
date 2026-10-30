---
title: Articles
description: Paste an article URL and listen to it, with the place you left off saved.
---

Type `articles` or `/articles`.

![Article list with spoken position](../../../assets/screenshots/read-list.png)

The bar takes a URL. Enter adds it to the top of the list and keeps the title and publication. Each row shows where you are in the spoken article, the same way a podcast episode shows position of length (`1:30 of 5:00`).

Type `1` to open the first article.

![Player with the article under the controls](../../../assets/screenshots/read-player.png)

The player has the title, play and skip controls, the article text, and **open in browser**. The article text and the playback position are in the account snapshot and in an S3 backup, so a phone and the web app pick up the same place.

The web app fetches the page through `/api/articles` on the same host, because article sites do not allow a browser to read them directly. A trailing slash the site requires is kept, including when the site redirects to add one. Saving an article asks the account host to generate the speech and store the file. Play uses that stored file. It does not call xAI from the browser, and a saved file plays without an xAI sign-in. The voice control sits beside the speed and defaults to Leo, on the web app and on the phone. An article is one speech request. The list shows preparing audio, then audio ready. A failure stays on the row as audio failed, with the reason. Each row, and the player, shows generating… while speech is being made. The phone has a download control after the file exists. The web app does not: a download would replace the page. The phone also asks the account host to generate speech, and plays a file already on the phone without that request. Private and non-http links are refused.

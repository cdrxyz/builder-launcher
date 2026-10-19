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

The web app fetches the page through `/api/articles` on the same host, because article sites do not allow a browser to read them directly. A trailing slash the site requires is kept, including when the site redirects to add one. Play uses the browser voice when it has one. Otherwise it uses the signed-in xAI account through Grok text to speech. Saving an article starts that speech and keeps the audio in the account store, so a later play does not wait on a live request. The voice control sits beside the speed and defaults to Leo. An article is one speech request. The browser starts play as the first speech arrives, then saves the finished file. A stored file also streams, including after a reload. The list shows preparing audio, then audio ready. A failure stays on the row as audio failed, with the reason. A Groq key is the free fallback and still speaks live. The phone still uses the system voice, and a signed-in phone stores the same audio and shows the same preparing, ready, or failed line. Private and non-http links are refused.

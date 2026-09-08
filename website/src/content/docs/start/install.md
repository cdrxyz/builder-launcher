---
title: Install
description: Install Builder Launcher with Obtainium or a signed GitHub Release APK.
---

## Obtainium (recommended)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium/releases).
2. Open this link on the phone (Obtainium must already be installed):

   https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/cdrxyz/builder-launcher

3. Confirm these fields if Obtainium asks:

   | Field | Value |
   | --- | --- |
   | App source URL | `https://github.com/cdrxyz/builder-launcher` |
   | App ID | `xyz.cdr.builderlauncher` |
   | APK filter | `builder-launcher-release` |
   | Include prereleases | off (on only if you want APKs from open pull requests) |

4. Add the app, install the APK, then open **Builder Launcher**.

## Sideload

Download `builder-launcher-release.apk` from [Releases](https://github.com/cdrxyz/builder-launcher/releases) and install it.

Every pull request also publishes a **prerelease** tagged `pr-<number>` (replaced on each push to that PR). Merges to `master` publish a latest GitHub Release so Obtainium can update.

## Set as Home

Android asks once to make Builder Launcher the default Home app. If you declined, open Settings (tap the clock) → **Set as default home app**. See [Default home app](../configure/default-home/).

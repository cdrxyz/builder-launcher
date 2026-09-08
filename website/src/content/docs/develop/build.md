---
title: Build from source
description: JDK 17 and Gradle via Hermit. Signed release APKs.
---

JDK 17 and Gradle 8.11.1 are pinned with [Hermit](https://cashapp.github.io/hermit/) (`bin/`). Android SDK 35.

```bash
source bin/activate-hermit
gradle :app:testDebugUnitTest :app:assembleRelease
```

`./bin/gradle` works without sourcing. Do not rely on a machine-wide JDK or Gradle.

Release signing uses PKCS12 env vars:

| Variable | Purpose |
| --- | --- |
| `ANDROID_KEYSTORE_PATH` | Path to the `.p12` |
| `ANDROID_KEYSTORE_PASSWORD` | Store password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

CI (`.github/workflows/ci.yml`) runs unit tests, Paparazzi verify, and a signed assemble. Pull requests publish prerelease `pr-<number>`. Merges to `master` publish the latest GitHub Release.

Application id: `xyz.cdr.builderlauncher`. APK asset name: `builder-launcher-release.apk`.

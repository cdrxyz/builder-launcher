#!/usr/bin/env python3
"""Fetch Android Open Source Project alarm/ringtone clips into res/raw.

Sources are Apache-2.0 AOSP sounds from platform/frameworks/base.
https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/data/sounds/
"""

from __future__ import annotations

import urllib.request
from pathlib import Path

BASE = "https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/data/sounds"

# Keep ClockSound names; map each to a real 14-24s AOSP clip.
TONES = {
    "clock_pulse": "alarms/ogg/Neon.ogg",
    "clock_chime": "ringtones/ogg/Callisto.ogg",
    "clock_bell": "ringtones/ogg/Titania.ogg",
    "clock_orthodox": "ringtones/ogg/Kuma.ogg",
    "clock_hum": "ringtones/ogg/Themos.ogg",
}


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    raw = root / "app" / "src" / "main" / "res" / "raw"
    raw.mkdir(parents=True, exist_ok=True)
    for stale in raw.glob("clock_*.wav"):
        stale.unlink()
        print(f"removed {stale.name}")
    for name, rel in TONES.items():
        dest = raw / f"{name}.ogg"
        url = f"{BASE}/{rel}"
        print(f"GET {url}")
        with urllib.request.urlopen(url) as resp:
            data = resp.read()
        if data[:4] != b"OggS":
            raise SystemExit(f"{name}: not an Ogg bitstream ({len(data)} bytes)")
        dest.write_bytes(data)
        print(f"{name}: {len(data)} bytes -> {dest}")


if __name__ == "__main__":
    main()

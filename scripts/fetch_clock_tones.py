#!/usr/bin/env python3
"""Fetch Android Open Source Project alarm/ringtone clips into res/raw.

Sources are Apache-2.0 AOSP sounds from platform/frameworks/base, plus a
public-domain Orthodox chant loop for clock_orthodox.

https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/data/sounds/
"""

from __future__ import annotations

import subprocess
import tempfile
import urllib.request
from pathlib import Path

BASE = "https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/data/sounds"

# Keep ClockSound names; map each to a real 14-24s AOSP clip.
TONES = {
    "clock_pulse": "alarms/ogg/Neon.ogg",
    "clock_chime": "ringtones/ogg/Callisto.ogg",
    "clock_bell": "ringtones/ogg/Titania.ogg",
}

CHANT_URL = (
    "https://commons.wikimedia.org/wiki/Special:FilePath/"
    "Byzantine_Ecclesiastical_Hymns_Part2.ogg"
)
USER_AGENT = "BuilderLauncher/1.0 (https://github.com/cdrxyz/builder-launcher)"


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req) as resp:
        return resp.read()


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
        data = fetch(url)
        if data[:4] != b"OggS":
            raise SystemExit(f"{name}: not an Ogg bitstream ({len(data)} bytes)")
        dest.write_bytes(data)
        print(f"{name}: {len(data)} bytes -> {dest}")
    write_hum(raw / "clock_hum.ogg")
    write_orthodox(raw / "clock_orthodox.ogg")


def write_hum(dest: Path) -> None:
    url = f"{BASE}/alarms/ogg/Helium.ogg"
    print(f"GET {url}")
    data = fetch(url)
    with tempfile.NamedTemporaryFile(suffix=".ogg", delete=True) as src:
        src.write(data)
        src.flush()
        # Helium is ~8.8s; loop it once so the clip sits in the 10-30s window.
        cmd = [
            "ffmpeg",
            "-y",
            "-stream_loop",
            "1",
            "-i",
            src.name,
            "-t",
            "17.6",
            "-c",
            "copy",
            "-metadata",
            "ANDROID_LOOP=true",
            "-metadata",
            "TITLE=Helium",
            str(dest),
        ]
        subprocess.run(cmd, check=True)
    print(f"clock_hum: {dest.stat().st_size} bytes -> {dest}")


def write_orthodox(dest: Path) -> None:
    print(f"GET {CHANT_URL}")
    data = fetch(CHANT_URL)
    with tempfile.NamedTemporaryFile(suffix=".ogg", delete=True) as src:
        src.write(data)
        src.flush()
        # Skip spoken/quiet lead-in; keep a 18s choir loop in the 10-30s window.
        cmd = [
            "ffmpeg",
            "-y",
            "-ss",
            "48",
            "-t",
            "18",
            "-i",
            src.name,
            "-af",
            "aformat=channel_layouts=stereo,afade=t=in:st=0:d=0.4,afade=t=out:st=17.5:d=0.5",
            "-c:a",
            "vorbis",
            "-strict",
            "-2",
            "-metadata",
            "ANDROID_LOOP=true",
            "-metadata",
            "TITLE=Orthodox chant",
            str(dest),
        ]
        subprocess.run(cmd, check=True)
    print(f"clock_orthodox: {dest.stat().st_size} bytes -> {dest}")


if __name__ == "__main__":
    main()

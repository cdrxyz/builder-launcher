#!/usr/bin/env python3
"""Generate original 10-30s loopable clock alert tones as 16-bit mono WAV.

Commons alarm recordings were too short, noisy, or GFDL/CC-BY-SA.
"""

from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

SR = 22_050
AMP = 0.62


def clamp(v: float) -> int:
    n = int(v * 32767.0)
    return max(-32767, min(32767, n))


def fade(i: int, n: int, attack: int = 400, release: int = 800) -> float:
    if n <= 0:
        return 0.0
    if i < attack:
        return i / attack
    remain = n - 1 - i
    if remain < release:
        return max(0.0, remain / release)
    return 1.0


def sine(hz: float, t: float) -> float:
    return math.sin(2.0 * math.pi * hz * t)


def mix_tone(buf: list[float], start: int, length: int, hz: float, amp: float, attack: int = 400, release: int = 800) -> None:
    end = min(len(buf), start + length)
    n = end - start
    for i in range(n):
        t = i / SR
        buf[start + i] += amp * fade(i, n, attack, release) * sine(hz, t)


def mix_bell(buf: list[float], start: int, length: int, fundamental: float, amp: float) -> None:
    end = min(len(buf), start + length)
    n = end - start
    partials = (
        (1.00, 1.00),
        (2.00, 0.38),
        (3.01, 0.18),
        (4.16, 0.10),
        (5.40, 0.06),
    )
    for i in range(n):
        t = i / SR
        env = math.exp(-t * 1.55) * fade(i, n, 80, 1200)
        sample = 0.0
        for mul, gain in partials:
            sample += gain * sine(fundamental * mul, t)
        buf[start + i] += amp * env * sample


def write_wav(path: Path, buf: list[float]) -> None:
    peak = max(abs(x) for x in buf) or 1.0
    scale = AMP / peak
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", clamp(x * scale)) for x in buf))


def pulse() -> list[float]:
    # 12s: 10 × 1.2s G3 pulses. Loops on the last rest.
    seconds = 12
    buf = [0.0] * (SR * seconds)
    on = int(SR * 0.38)
    period = int(SR * 1.2)
    for n in range(10):
        mix_tone(buf, n * period, on, 196.00, 0.9, attack=180, release=900)
    return buf


def chime() -> list[float]:
    # 16s: four 4s phrases, G4 then C5.
    seconds = 16
    buf = [0.0] * (SR * seconds)
    a = int(SR * 0.55)
    b = int(SR * 0.85)
    gap = int(SR * 0.18)
    for n in range(4):
        start = n * SR * 4
        mix_tone(buf, start, a, 392.00, 0.72, attack=200, release=1800)
        mix_tone(buf, start + a + gap, b, 523.25, 0.62, attack=200, release=2600)
    return buf


def bell() -> list[float]:
    # 18s: three strikes, 6s apart, long decay so the loop is a peal.
    seconds = 18
    buf = [0.0] * (SR * seconds)
    length = int(SR * 5.8)
    mix_bell(buf, 0, length, 196.00, 1.0)
    mix_bell(buf, SR * 6, length, 174.61, 0.92)
    mix_bell(buf, SR * 12, length, 146.83, 0.88)
    return buf


def orthodox() -> list[float]:
    # 16s: ison plus a slow four-bar phrase. Loops on the drone.
    seconds = 16
    buf = [0.0] * (SR * seconds)
    n = len(buf)
    mix_tone(buf, 0, n, 146.83, 0.28, attack=2000, release=2000)
    mix_tone(buf, 0, n, 293.66, 0.08, attack=2000, release=2000)
    beat = SR * 4
    mix_tone(buf, 0, beat * 2, 220.00, 0.42, attack=600, release=1800)
    mix_tone(buf, beat, beat * 2, 196.00, 0.38, attack=600, release=1800)
    mix_tone(buf, beat * 2, beat * 2, 174.61, 0.40, attack=600, release=1800)
    mix_tone(buf, beat * 3, beat, 146.83, 0.36, attack=600, release=2200)
    return buf


def hum() -> list[float]:
    # 12s: low A2 with a 4s tremolo cycle (3 cycles).
    seconds = 12
    buf = [0.0] * (SR * seconds)
    for i in range(len(buf)):
        t = i / SR
        trem = 0.78 + 0.22 * math.sin(2.0 * math.pi * 0.25 * t)
        env = fade(i, len(buf), 1200, 1200)
        sample = 0.72 * sine(110.0, t) + 0.22 * sine(165.0, t) + 0.10 * sine(220.0, t)
        buf[i] = env * trem * sample
    return buf


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    raw = root / "app" / "src" / "main" / "res" / "raw"
    raw.mkdir(parents=True, exist_ok=True)
    tones = {
        "clock_pulse": pulse,
        "clock_chime": chime,
        "clock_bell": bell,
        "clock_orthodox": orthodox,
        "clock_hum": hum,
    }
    for name, fn in tones.items():
        wav = raw / f"{name}.wav"
        write_wav(wav, fn())
        dur = wave.open(str(wav)).getnframes() / SR
        print(f"{name}: {dur:.1f}s -> {wav} ({wav.stat().st_size} bytes)")


if __name__ == "__main__":
    main()

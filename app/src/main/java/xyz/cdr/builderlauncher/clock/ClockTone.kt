package xyz.cdr.builderlauncher.clock

object ClockTone {
    const val MIN_LOOP_MS = 10_000L
    const val MAX_LOOP_MS = 30_000L

    fun loopMs(sound: ClockSound): Long = when (sound) {
        ClockSound.PULSE -> 12_000L
        ClockSound.CHIME -> 16_000L
        ClockSound.BELL -> 18_000L
        ClockSound.ORTHODOX -> 16_000L
        ClockSound.HUM -> 12_000L
        ClockSound.OFF -> 0L
    }
}

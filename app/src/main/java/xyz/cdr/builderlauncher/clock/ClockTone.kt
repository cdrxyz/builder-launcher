package xyz.cdr.builderlauncher.clock

object ClockTone {
    const val MIN_LOOP_MS = 10_000L
    const val MAX_LOOP_MS = 30_000L

    fun loopMs(sound: ClockSound): Long = when (sound) {
        ClockSound.PULSE -> 20_400L
        ClockSound.CHIME -> 14_222L
        ClockSound.BELL -> 21_179L
        ClockSound.ORTHODOX -> 23_529L
        ClockSound.HUM -> 21_179L
        ClockSound.OFF -> 0L
    }
}

package xyz.cdr.builderlauncher.clock

import kotlin.math.exp
import kotlin.math.sin

object ClockTone {
    const val SAMPLE_RATE = 22_050

    fun pcm(sound: ClockSound): ShortArray {
        if (sound.silent) return ShortArray(0)
        val seconds = if (sound == ClockSound.ORTHODOX) 4 else 2
        val out = ShortArray(SAMPLE_RATE * seconds)
        when (sound) {
            ClockSound.PULSE -> pulse(out)
            ClockSound.CHIME -> chime(out)
            ClockSound.BELL -> bell(out)
            ClockSound.ORTHODOX -> orthodox(out)
            ClockSound.HUM -> hum(out)
            ClockSound.OFF -> Unit
        }
        return out
    }

    private fun pulse(out: ShortArray) {
        val on = (SAMPLE_RATE * 0.45).toInt()
        sine(out, 196.0, 0, on, 0.55)
    }

    private fun chime(out: ShortArray) {
        val a = (SAMPLE_RATE * 0.35).toInt()
        val gap = (SAMPLE_RATE * 0.12).toInt()
        val b = (SAMPLE_RATE * 0.45).toInt()
        sine(out, 392.0, 0, a, 0.45)
        sine(out, 523.25, a + gap, b, 0.4)
    }

    private fun bell(out: ShortArray) {
        val len = (SAMPLE_RATE * 1.4).toInt()
        for (i in 0 until len.coerceAtMost(out.size)) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 2.2)
            val sample = env * (
                0.42 * sin(2.0 * Math.PI * 196.0 * t) +
                    0.16 * sin(2.0 * Math.PI * 392.0 * t)
                )
            out[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun orthodox(out: ShortArray) {
        val beat = SAMPLE_RATE
        mix(out, 146.83, 0, out.size, 0.20)
        mix(out, 293.66, 0, out.size, 0.06)
        mix(out, 220.00, 0, beat * 2, 0.24)
        mix(out, 196.00, beat, beat * 2, 0.22)
        mix(out, 174.61, beat * 2, beat * 2, 0.24)
        mix(out, 146.83, beat * 3, beat, 0.20)
    }

    private fun hum(out: ShortArray) {
        sine(out, 110.0, 0, out.size, 0.40)
    }

    private fun sine(out: ShortArray, hz: Double, start: Int, length: Int, amp: Double) {
        mixInto(out, hz, start, length, amp, replace = true)
    }

    private fun mix(out: ShortArray, hz: Double, start: Int, length: Int, amp: Double) {
        mixInto(out, hz, start, length, amp, replace = false)
    }

    private fun mixInto(
        out: ShortArray,
        hz: Double,
        start: Int,
        length: Int,
        amp: Double,
        replace: Boolean,
    ) {
        val end = (start + length).coerceAtMost(out.size)
        val fade = (SAMPLE_RATE * 0.08).toInt().coerceAtLeast(1)
        for (i in start until end) {
            val t = (i - start).toDouble() / SAMPLE_RATE
            val local = i - start
            val remain = end - 1 - i
            val env = when {
                local < fade -> local.toDouble() / fade
                remain < fade -> remain.toDouble() / fade
                else -> 1.0
            }
            val sample = (env * amp * sin(2.0 * Math.PI * hz * t) * Short.MAX_VALUE).toInt()
            val mixed = if (replace) sample else out[i] + sample
            out[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}

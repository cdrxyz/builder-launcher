package xyz.cdr.builderlauncher.clock

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ClockTab { Timer, Alarm, Zones }

object Clock {
    const val BACK = "<"
    const val COMMAND = "clock"
    val PRESETS_MIN = listOf(1, 5, 10, 15, 25, 30)
    const val DEFAULT_TIMER_MS = 5 * 60_000L
    const val SNOOZE_MS = 8 * 60_000L
    const val RAMP_MS = 4_000L
    const val PEAK_GAIN = 1.0f
    const val FLOOR_GAIN = 0.45f

    fun formatTimer(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%d:%02d".format(m, s)
        }
    }

    fun remainingMs(timer: TimerState, now: Long): Long {
        if (!timer.running || timer.endsAt == null) return timer.remainingMs.coerceAtLeast(0L)
        return (timer.endsAt - now).coerceAtLeast(0L)
    }

    fun homeClockLabel(timer: TimerState, now: Long, clockText: String): String {
        if (!timer.running) return clockText
        return formatTimer(remainingMs(timer, now))
    }

    fun start(timer: TimerState, now: Long): TimerState {
        val left = remainingMs(timer, now).coerceAtLeast(1L)
        return timer.copy(running = true, endsAt = now + left, remainingMs = left)
    }

    fun pause(timer: TimerState, now: Long): TimerState {
        val left = remainingMs(timer, now)
        return timer.copy(running = false, endsAt = null, remainingMs = left)
    }

    fun reset(timer: TimerState): TimerState =
        timer.copy(running = false, endsAt = null, remainingMs = timer.durationMs)

    fun setDuration(timer: TimerState, durationMs: Long): TimerState {
        val ms = durationMs.coerceIn(1_000L, 24 * 60 * 60_000L)
        return TimerState(durationMs = ms, remainingMs = ms)
    }

    fun parseTimer(raw: String): Long? {
        val t = raw.trim().lowercase()
        if (t.isEmpty()) return null
        val seconds = Regex("""^(\d+)\s*s(?:ec(?:onds?)?)?$""").matchEntire(t)
        if (seconds != null) {
            val n = seconds.groupValues[1].toLongOrNull() ?: return null
            return (n * 1000L).takeIf { it in 1_000L..24 * 60 * 60_000L }
        }
        val hm = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?$""").matchEntire(t)
        if (hm != null) {
            val a = hm.groupValues[1].toLong()
            val b = hm.groupValues[2].toLong()
            val c = hm.groupValues[3].toLongOrNull() ?: 0L
            if (b > 59 || c > 59) return null
            val ms = if (hm.groupValues[3].isNotEmpty()) {
                ((a * 3600) + (b * 60) + c) * 1000L
            } else {
                ((a * 60) + b) * 1000L
            }
            return ms.takeIf { it in 1_000L..24 * 60 * 60_000L }
        }
        val minutes = Regex("""^(\d+)$""").matchEntire(t) ?: return null
        val n = minutes.groupValues[1].toLongOrNull() ?: return null
        return (n * 60_000L).takeIf { it in 1_000L..24 * 60 * 60_000L }
    }

    fun parseAlarm(raw: String): Pair<Int, Int>? {
        val t = raw.trim().lowercase().replace(".", "")
        if (t.isEmpty()) return null
        val ampm = Regex("""^(\d{1,2})(?::(\d{2}))?\s*(a|am|p|pm)$""").matchEntire(t)
        if (ampm != null) {
            var hour = ampm.groupValues[1].toInt()
            val minute = ampm.groupValues[2].ifEmpty { "0" }.toInt()
            val pm = ampm.groupValues[3].startsWith("p")
            if (hour !in 1..12 || minute !in 0..59) return null
            hour = when {
                hour == 12 && !pm -> 0
                hour == 12 && pm -> 12
                pm -> hour + 12
                else -> hour
            }
            return hour to minute
        }
        val colon = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(t)
        if (colon != null) {
            val hour = colon.groupValues[1].toInt()
            val minute = colon.groupValues[2].toInt()
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour to minute
        }
        val compact = Regex("""^(\d{3,4})$""").matchEntire(t) ?: return null
        val n = compact.groupValues[1]
        val hour = if (n.length == 3) n.take(1).toInt() else n.take(2).toInt()
        val minute = n.takeLast(2).toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    fun formatAlarm(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    fun nextTrigger(
        hour: Int,
        minute: Int,
        now: Long,
        days: Set<Int> = emptySet(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val zoned = Instant.ofEpochMilli(now).atZone(zone)
        var candidate = zoned.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!candidate.isAfter(zoned)) candidate = candidate.plusDays(1)
        val wanted = days.filter { it in 1..7 }.toSet()
        if (wanted.isEmpty()) return candidate.toInstant().toEpochMilli()
        repeat(8) {
            if (candidate.dayOfWeek.value in wanted && candidate.isAfter(zoned)) {
                return candidate.toInstant().toEpochMilli()
            }
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant().toEpochMilli()
    }

    fun formatZoneTime(zoneId: String, now: Long): String {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("UTC"))
        return DateTimeFormatter.ofPattern("HH:mm", Locale.US)
            .format(Instant.ofEpochMilli(now).atZone(zone))
    }

    fun formatZoneDate(zoneId: String, now: Long): String {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("UTC"))
        val there = Instant.ofEpochMilli(now).atZone(zone)
        val here = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
        val date = fmt.format(there)
        val offsetHours = java.time.Duration.between(
            here.toLocalDateTime(),
            there.toLocalDateTime(),
        ).toHours()
        val sign = if (offsetHours >= 0) "+" else ""
        return "$date  $sign${offsetHours}h"
    }

    fun offsetHours(zoneId: String, now: Long): Long {
        val there = Instant.ofEpochMilli(now).atZone(runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("UTC")))
        val here = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        return java.time.Duration.between(here.toLocalDateTime(), there.toLocalDateTime()).toHours()
    }

    fun fadeGain(
        elapsedMs: Long,
        rampMs: Long = RAMP_MS,
        peak: Float = PEAK_GAIN,
        floor: Float = FLOOR_GAIN,
    ): Float {
        val start = floor.coerceIn(0f, peak)
        if (elapsedMs <= 0L) return start
        if (elapsedMs >= rampMs) return peak
        return start + (peak - start) * (elapsedMs.toFloat() / rampMs.toFloat())
    }

    fun useMediaStream(alarmVolume: Int, preview: Boolean): Boolean = preview || alarmVolume <= 0

    fun fireTimer(timer: TimerState): TimerFire {
        val duration = timer.durationMs.coerceAtLeast(1_000L)
        return TimerFire(
            timer = reset(timer),
            alert = ClockAlert(kind = ClockAlertKind.TIMER, durationMs = duration),
        )
    }

    fun runAgain(alert: ClockAlert, now: Long): TimerState {
        val duration = alert.durationMs.coerceAtLeast(1_000L)
        return start(TimerState(durationMs = duration, remainingMs = duration), now)
    }

    fun fireAlarm(alarm: ClockAlarm): AlarmFire {
        return AlarmFire(
            alarm = alarm.copy(snoozeUntil = null),
            alert = ClockAlert(
                kind = ClockAlertKind.ALARM,
                alarmId = alarm.id,
                hour = alarm.hour,
                minute = alarm.minute,
                label = alarm.label,
            ),
        )
    }

    fun snooze(alarm: ClockAlarm, now: Long): ClockAlarm {
        return alarm.copy(enabled = true, snoozeUntil = now + SNOOZE_MS)
    }

    fun nextFireAt(
        alarm: ClockAlarm,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val snooze = alarm.snoozeUntil
        if (snooze != null) return if (snooze > now) snooze else now
        return nextTrigger(alarm.hour, alarm.minute, now, alarm.days, zone)
    }
}

@Serializable
data class TimerState(
    val durationMs: Long = Clock.DEFAULT_TIMER_MS,
    val remainingMs: Long = Clock.DEFAULT_TIMER_MS,
    val running: Boolean = false,
    val endsAt: Long? = null,
)

@Serializable
data class ClockAlarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val label: String = "",
    val days: Set<Int> = emptySet(),
    val snoozeUntil: Long? = null,
)

enum class ClockAlertKind { TIMER, ALARM }

@Serializable
data class ClockAlert(
    val kind: ClockAlertKind,
    val durationMs: Long = 0,
    val alarmId: String = "",
    val hour: Int = 0,
    val minute: Int = 0,
    val label: String = "",
)

data class TimerFire(val timer: TimerState, val alert: ClockAlert)

data class AlarmFire(val alarm: ClockAlarm, val alert: ClockAlert)

enum class ClockSound {
    PULSE,
    CHIME,
    BELL,
    ORTHODOX,
    HUM,
    OFF,
    ;

    val label: String get() = name.lowercase()
    val silent: Boolean get() = this == OFF

    companion object {
        fun parse(raw: String?): ClockSound =
            entries.find { it.name.equals(raw, ignoreCase = true) } ?: PULSE
    }
}

@Serializable
data class WorldClock(
    val id: String,
    val label: String,
    val zoneId: String,
)

@Serializable
data class ClockSnapshot(
    val timer: TimerState = TimerState(),
    val alarms: List<ClockAlarm> = emptyList(),
    val zones: List<WorldClock> = emptyList(),
    val alert: ClockAlert? = null,
)

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
    const val TIMER_TICK_MS = 200L
    const val ANALOG_TICK_MS = 1_000L
    const val DIGITAL_TICK_MS = 15_000L

    fun homeTickMs(timerRunning: Boolean, analog: Boolean): Long = when {
        timerRunning -> TIMER_TICK_MS
        analog -> ANALOG_TICK_MS
        else -> DIGITAL_TICK_MS
    }

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
        val cap = timer.durationMs.coerceAtLeast(0L)
        if (!timer.running || timer.endsAt == null) return timer.remainingMs.coerceIn(0L, cap)
        return (timer.endsAt - now).coerceIn(0L, cap)
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

    fun setDuration(timer: TimerState, durationMs: Long, label: String = ""): TimerState {
        val ms = durationMs.coerceIn(1_000L, 24 * 60 * 60_000L)
        return TimerState(durationMs = ms, remainingMs = ms, label = label.trim())
    }

    fun parseTimer(raw: String): Long? = parseTimerInput(raw)?.durationMs

    fun parseTimerInput(raw: String): ParsedTimer? {
        val original = raw.trim()
        if (original.isEmpty()) return null
        parseTimerDuration(original)?.let { return ParsedTimer(it) }
        val found = findTimerDuration(original) ?: return null
        val label = cleanTimerLabel(original.removeRange(found.second).replace(Regex("\\s+"), " ").trim())
        return ParsedTimer(found.first, label)
    }

    fun parseAlarm(raw: String): Pair<Int, Int>? = parseAlarmInput(raw)?.let { it.hour to it.minute }

    fun parseAlarmInput(raw: String): ParsedAlarm? {
        val original = raw.trim()
        if (original.isEmpty()) return null
        val (withoutDays, days) = extractDays(original)
        val timeHit = findAlarmTime(withoutDays) ?: return null
        val leftover = withoutDays.removeRange(timeHit.second).replace(Regex("\\s+"), " ").trim()
        return ParsedAlarm(timeHit.first.first, timeHit.first.second, cleanAlarmLabel(leftover), days)
    }

    fun formatAlarm(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    fun formatDays(days: Set<Int>): String {
        val wanted = days.filter { it in 1..7 }.toSortedSet()
        if (wanted.isEmpty()) return ""
        if (wanted == (1..7).toSortedSet()) return "daily"
        if (wanted == (1..5).toSortedSet()) return "weekdays"
        if (wanted == sortedSetOf(6, 7)) return "weekends"
        val names = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return wanted.joinToString(" ") { names[it] }
    }

    fun alarmStatus(alarm: ClockAlarm): String {
        val on = if (alarm.enabled) "on" else "off"
        val days = formatDays(alarm.days)
        return if (days.isEmpty()) on else "$on  $days"
    }

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

    fun useMediaStream(preview: Boolean): Boolean = preview

    fun fireTimer(timer: TimerState): TimerFire {
        val duration = timer.durationMs.coerceAtLeast(1_000L)
        return TimerFire(
            timer = reset(timer),
            alert = ClockAlert(kind = ClockAlertKind.TIMER, durationMs = duration, label = timer.label),
        )
    }

    fun runAgain(alert: ClockAlert, now: Long): TimerState {
        val duration = alert.durationMs.coerceAtLeast(1_000L)
        return start(TimerState(durationMs = duration, remainingMs = duration, label = alert.label), now)
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

    private fun parseTimerDuration(raw: String): Long? {
        val t = raw.trim().lowercase()
        if (t.isEmpty()) return null
        Regex("""^(\d+)\s*(?:hours?|hrs?|h)\s*(?:and\s*)?(\d+)\s*(?:minutes?|mins?|m)?$""").matchEntire(t)?.let { m ->
            val h = m.groupValues[1].toLong()
            val min = m.groupValues[2].toLong()
            return (((h * 3600) + (min * 60)) * 1000L).takeIf { it in 1_000L..24 * 60 * 60_000L }
        }
        Regex("""^(\d+)\s*(?:hours?|hrs?|h)$""").matchEntire(t)?.let { m ->
            val n = m.groupValues[1].toLong()
            return (n * 3_600_000L).takeIf { it in 1_000L..24 * 60 * 60_000L }
        }
        Regex("""^(\d+)\s*(?:minutes?|mins?|m)$""").matchEntire(t)?.let { m ->
            val n = m.groupValues[1].toLong()
            return (n * 60_000L).takeIf { it in 1_000L..24 * 60 * 60_000L }
        }
        Regex("""^(\d+)\s*(?:seconds?|secs?|s)$""").matchEntire(t)?.let { m ->
            val n = m.groupValues[1].toLong()
            return (n * 1000L).takeIf { it in 1_000L..24 * 60 * 60_000L }
        }
        Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?$""").matchEntire(t)?.let { hm ->
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

    private fun findTimerDuration(raw: String): Pair<Long, IntRange>? {
        fun last(re: Regex, convert: (MatchResult) -> Long?): Pair<Long, IntRange>? {
            for (m in re.findAll(raw).toList().asReversed()) {
                val ms = convert(m)?.takeIf { it in 1_000L..24 * 60 * 60_000L } ?: continue
                return ms to m.range
            }
            return null
        }
        last(Regex("""(?i)(\d+)\s*(?:hours?|hrs?|h)\s*(?:and\s*)?(\d+)\s*(?:minutes?|mins?|m)?""")) { m ->
            val h = m.groupValues[1].toLong()
            val min = m.groupValues[2].toLong()
            ((h * 3600) + (min * 60)) * 1000L
        }?.let { return it }
        last(Regex("""(?i)(\d+)\s*(?:hours?|hrs?|h)""")) { m ->
            m.groupValues[1].toLong() * 3_600_000L
        }?.let { return it }
        last(Regex("""(?i)(\d+)\s*(?:minutes?|mins?|m)""")) { m ->
            m.groupValues[1].toLong() * 60_000L
        }?.let { return it }
        last(Regex("""(?i)(\d+)\s*(?:seconds?|secs?|s)""")) { m ->
            m.groupValues[1].toLong() * 1000L
        }?.let { return it }
        last(Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")) { hm ->
            val a = hm.groupValues[1].toLong()
            val b = hm.groupValues[2].toLong()
            val c = hm.groupValues[3].toLongOrNull() ?: 0L
            if (b > 59 || c > 59) return@last null
            if (hm.groupValues[3].isNotEmpty()) {
                ((a * 3600) + (b * 60) + c) * 1000L
            } else {
                ((a * 60) + b) * 1000L
            }
        }?.let { return it }
        last(Regex("""(?<![A-Za-z0-9:])(\d+)(?![A-Za-z0-9:])""")) { m ->
            m.groupValues[1].toLong() * 60_000L
        }?.let { return it }
        return null
    }

    private fun parseAlarmTime(raw: String): Pair<Int, Int>? {
        val t = raw.trim().lowercase().replace(".", "")
        if (t.isEmpty()) return null
        Regex("""^(\d{1,2})(?::(\d{2}))?\s*(am|pm|a|p)$""").matchEntire(t)?.let { ampm ->
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
        Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(t)?.let { colon ->
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

    private fun findAlarmTime(raw: String): Pair<Pair<Int, Int>, IntRange>? {
        parseAlarmTime(raw.trim())?.let { return it to IntRange(0, raw.length - 1) }
        val re = Regex("""(?i)(\d{1,2}:\d{2}\s*(?:am|pm|a|p)?|\d{1,2}\s*(?:am|pm|a|p)|\d{3,4})""")
        for (m in re.findAll(raw).toList().asReversed()) {
            val hm = parseAlarmTime(m.value) ?: continue
            return hm to m.range
        }
        return null
    }

    private fun extractDays(raw: String): Pair<String, Set<Int>> {
        var text = raw
        val days = mutableSetOf<Int>()
        fun strip(pattern: String, add: Iterable<Int>) {
            val re = Regex(pattern, RegexOption.IGNORE_CASE)
            if (re.containsMatchIn(text)) {
                days += add
                text = text.replace(re, " ")
            }
        }
        strip("""\b(?:every\s+)?weekdays?\b""", 1..5)
        strip("""\b(?:every\s+)?weekends?\b""", 6..7)
        strip("""\b(?:every\s+day|everyday|daily)\b""", 1..7)
        val aliases = listOf(
            1 to listOf("mondays", "monday", "mon"),
            2 to listOf("tuesdays", "tuesday", "tues", "tue"),
            3 to listOf("wednesdays", "wednesday", "weds", "wed"),
            4 to listOf("thursdays", "thursday", "thurs", "thu"),
            5 to listOf("fridays", "friday", "fri"),
            6 to listOf("saturdays", "saturday", "sat"),
            7 to listOf("sundays", "sunday", "sun"),
        )
        for ((n, words) in aliases) {
            strip("""\b(?:every\s+)?(?:${words.joinToString("|")})\b""", listOf(n))
        }
        text = text.replace(Regex("(?i)\\bevery\\b"), " ")
        text = text.replace(Regex("\\s+"), " ").trim()
        return text to days
    }

    private fun cleanTimerLabel(raw: String): String {
        return raw.trim()
            .replace(Regex("(?i)^(for|in|a|an)\\s+"), "")
            .replace(Regex("(?i)\\s+timer$"), "")
            .trim()
    }

    private fun cleanAlarmLabel(raw: String): String {
        return raw.trim()
            .replace(Regex("(?i)\\b(set|an|a|alarm|for|at)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

data class ParsedTimer(val durationMs: Long, val label: String = "")

data class ParsedAlarm(
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val days: Set<Int> = emptySet(),
)

@Serializable
data class TimerState(
    val durationMs: Long = Clock.DEFAULT_TIMER_MS,
    val remainingMs: Long = Clock.DEFAULT_TIMER_MS,
    val running: Boolean = false,
    val endsAt: Long? = null,
    val label: String = "",
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

@Serializable
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

@Serializable
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

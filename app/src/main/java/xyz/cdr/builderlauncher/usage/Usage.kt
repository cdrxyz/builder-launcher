package xyz.cdr.builderlauncher.usage

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class UsageKind {
    PRODUCTIVE,
    DISTRACTING,
    OTHER,
    ;

    val label: String
        get() = name.lowercase()

    fun next(): UsageKind = when (this) {
        OTHER -> PRODUCTIVE
        PRODUCTIVE -> DISTRACTING
        DISTRACTING -> OTHER
    }
}

enum class UsagePeriod(val label: String) {
    TODAY("today"),
    WEEK("7D"),
    MONTH("30D"),
    HALF("6M"),
}

data class UsageApp(
    val packageName: String,
    val label: String,
    val millis: Long,
    val kind: UsageKind,
)

data class UsageBar(
    val startMs: Long,
    val label: String,
    val detail: String,
    val productiveMs: Long,
    val distractingMs: Long,
    val otherMs: Long,
) {
    val totalMs: Long get() = productiveMs + distractingMs + otherMs
}

data class UsageSnapshot(
    val granted: Boolean,
    val period: UsagePeriod,
    val totalMs: Long,
    val productiveMs: Long,
    val distractingMs: Long,
    val otherMs: Long,
    val vsYesterdayMs: Long?,
    val pickups: Int,
    val bars: List<UsageBar>,
    val apps: List<UsageApp>,
) {
    val otherShare: Int get() = Usage.percent(otherMs, totalMs)
    val productiveShare: Int get() = Usage.percent(productiveMs, totalMs)
    val distractingShare: Int get() = Usage.percent(distractingMs, totalMs)
}

data class UsageRawApp(
    val packageName: String,
    val label: String,
    val millis: Long,
)

data class UsageRawDay(
    val startMs: Long,
    val apps: List<UsageRawApp>,
    val pickups: Int = 0,
)

object Usage {
    const val COMMAND = "usage"
    const val BACK = "<"
    const val DAYS = 7
    const val MONTH_DAYS = 30
    const val HALF_WEEKS = 26
    const val HOURS = 24
    const val TOP_APPS = 12
    const val MIN_MS = 1_000L
    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 86_400_000L
    const val WEEK_MS = 7 * DAY_MS

    fun formatDuration(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val hours = total / HOUR_MS
        val minutes = (total % HOUR_MS) / 60_000L
        val seconds = (total % 60_000L) / 1_000L
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            total > 0 -> "${seconds}s"
            else -> "0m"
        }
    }

    fun percent(part: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((part.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
    }

    fun vsLabel(deltaMs: Long?): String {
        if (deltaMs == null) return "No yesterday yet"
        return when {
            deltaMs > 0 -> "${formatDuration(deltaMs)} more than yesterday"
            deltaMs < 0 -> "${formatDuration(-deltaMs)} less than yesterday"
            else -> "Same as yesterday"
        }
    }

    fun defaultKind(packageName: String): UsageKind {
        val pkg = packageName.lowercase()
        if (DISTRACTING.any { it in pkg }) return UsageKind.DISTRACTING
        if (PRODUCTIVE.any { it in pkg }) return UsageKind.PRODUCTIVE
        return UsageKind.OTHER
    }

    fun isNoise(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg == "android" ||
            pkg.startsWith("com.android.systemui") ||
            pkg.contains("permissioncontroller") ||
            pkg.contains("inputmethod") ||
            pkg.contains("quicksearchbox")
    }

    fun kindOf(packageName: String, overrides: Map<String, UsageKind>): UsageKind {
        return overrides[packageName] ?: defaultKind(packageName)
    }

    fun indexAt(x: Float, width: Float, count: Int): Int {
        if (count <= 0 || width <= 0f) return 0
        return (x / width * count).toInt().coerceIn(0, count - 1)
    }

    fun axisLabel(index: Int, count: Int, period: UsagePeriod): Boolean {
        if (count <= 0) return false
        val last = count - 1
        return when (period) {
            UsagePeriod.TODAY -> index == 0 || index == last || index % 6 == 0
            UsagePeriod.WEEK -> true
            UsagePeriod.MONTH -> index == 0 || index == last || index % 5 == 0
            UsagePeriod.HALF -> index == 0 || index == last || index % 4 == 0
        }
    }

    fun barLabel(startMs: Long, period: UsagePeriod, zone: ZoneId): String {
        val instant = Instant.ofEpochMilli(startMs)
        return when (period) {
            UsagePeriod.TODAY -> DateTimeFormatter.ofPattern("H", Locale.US).withZone(zone).format(instant)
            UsagePeriod.WEEK -> DateTimeFormatter.ofPattern("EEEEE", Locale.US).withZone(zone).format(instant)
            UsagePeriod.MONTH -> DateTimeFormatter.ofPattern("d", Locale.US).withZone(zone).format(instant)
            UsagePeriod.HALF -> DateTimeFormatter.ofPattern("MMM d", Locale.US).withZone(zone).format(instant)
        }
    }

    fun barDetail(startMs: Long, period: UsagePeriod, zone: ZoneId): String {
        val instant = Instant.ofEpochMilli(startMs)
        return when (period) {
            UsagePeriod.TODAY -> DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US).withZone(zone).format(instant)
            UsagePeriod.WEEK, UsagePeriod.MONTH ->
                DateTimeFormatter.ofPattern("EEE d MMM", Locale.US).withZone(zone).format(instant)
            UsagePeriod.HALF -> {
                val end = Instant.ofEpochMilli(startMs + WEEK_MS - 1)
                val startText = DateTimeFormatter.ofPattern("d MMM", Locale.US).withZone(zone).format(instant)
                val endText = DateTimeFormatter.ofPattern("d MMM", Locale.US).withZone(zone).format(end)
                "$startText – $endText"
            }
        }
    }

    fun totalOf(raw: UsageRawDay): Long {
        return raw.apps.sumOf { app ->
            if (app.millis < MIN_MS || isNoise(app.packageName)) 0L else app.millis
        }
    }

    fun build(
        rawDays: List<UsageRawDay>,
        overrides: Map<String, UsageKind>,
        period: UsagePeriod,
        granted: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        previousMs: Long? = null,
    ): UsageSnapshot {
        val bars = rawDays.map { raw ->
            var productive = 0L
            var distracting = 0L
            var other = 0L
            raw.apps.forEach { app ->
                if (app.millis < MIN_MS || isNoise(app.packageName)) return@forEach
                when (kindOf(app.packageName, overrides)) {
                    UsageKind.PRODUCTIVE -> productive += app.millis
                    UsageKind.DISTRACTING -> distracting += app.millis
                    UsageKind.OTHER -> other += app.millis
                }
            }
            UsageBar(
                startMs = raw.startMs,
                label = barLabel(raw.startMs, period, zone),
                detail = barDetail(raw.startMs, period, zone),
                productiveMs = productive,
                distractingMs = distracting,
                otherMs = other,
            )
        }
        val apps = aggregateApps(rawDays, overrides)
        val total = bars.sumOf { it.totalMs }
        val productive = bars.sumOf { it.productiveMs }
        val distracting = bars.sumOf { it.distractingMs }
        val other = bars.sumOf { it.otherMs }
        val vs = if (period == UsagePeriod.TODAY) previousMs?.let { total - it } else null
        return UsageSnapshot(
            granted = granted,
            period = period,
            totalMs = total,
            productiveMs = productive,
            distractingMs = distracting,
            otherMs = other,
            vsYesterdayMs = vs,
            pickups = rawDays.sumOf { it.pickups },
            bars = bars,
            apps = apps,
        )
    }

    fun sample(period: UsagePeriod = UsagePeriod.TODAY): UsageSnapshot {
        val zone = ZoneOffset.UTC
        val dayStart = 1_725_667_200_000L
        return when (period) {
            UsagePeriod.TODAY -> build(
                rawDays = sampleHours(dayStart + DAY_MS * 6),
                overrides = emptyMap(),
                period = period,
                granted = true,
                zone = zone,
                previousMs = 3 * HOUR_MS + 35 * 60_000L,
            )
            UsagePeriod.WEEK -> build(sampleDays(dayStart, DAYS), emptyMap(), period, true, zone)
            UsagePeriod.MONTH -> build(sampleDays(dayStart - DAY_MS * 23, MONTH_DAYS), emptyMap(), period, true, zone)
            UsagePeriod.HALF -> build(sampleWeeks(dayStart - WEEK_MS * 19, HALF_WEEKS), emptyMap(), period, true, zone)
        }
    }

    private fun sampleHours(dayStart: Long): List<UsageRawDay> {
        return (0 until HOURS).map { hour ->
            val weight = hourWeight(hour)
            UsageRawDay(
                startMs = dayStart + hour * HOUR_MS,
                apps = if (weight <= 0f) emptyList() else sampleApps(weight),
                pickups = if (weight <= 0f) 0 else (weight * 3).roundToInt().coerceAtLeast(1),
            )
        }
    }

    private fun sampleDays(start: Long, count: Int): List<UsageRawDay> {
        val days = listOf(
            Triple(2.0, 1.5, 0.7),
            Triple(3.0, 2.0, 0.8),
            Triple(1.3, 3.0, 1.2),
            Triple(4.0, 0.0, 1.7),
            Triple(1.5, 1.3, 2.0),
            Triple(2.3, 0.8, 0.4),
            Triple(3.2, 1.3, 0.7),
        )
        return (0 until count).map { i ->
            val (prodH, distH, otherH) = days[i % days.size]
            val scale = if (i % 7 == 2) 1.15 else 1.0
            UsageRawDay(
                startMs = start + i * DAY_MS,
                apps = listOf(
                    UsageRawApp("com.termux", "Termux", (prodH * scale * HOUR_MS).toLong()),
                    UsageRawApp("com.google.android.youtube", "YouTube", (distH * scale * HOUR_MS).toLong()),
                    UsageRawApp("org.mozilla.firefox", "Firefox", (otherH * scale * HOUR_MS).toLong()),
                    UsageRawApp("com.slack", "Slack", if (i % 3 == 0) (0.9 * HOUR_MS).toLong() else 0L),
                    UsageRawApp("com.google.android.gm", "Gmail", if (i % 2 == 0) (0.4 * HOUR_MS).toLong() else 0L),
                    UsageRawApp("com.instagram.android", "Instagram", if (i % 4 == 2) (0.5 * HOUR_MS).toLong() else 0L),
                ).filter { it.millis >= MIN_MS },
                pickups = 14 + (i % 9) * 2,
            )
        }
    }

    private fun sampleWeeks(start: Long, count: Int): List<UsageRawDay> {
        return (0 until count).map { i ->
            val scale = 4.5 + (i % 5) * 0.8
            UsageRawDay(
                startMs = start + i * WEEK_MS,
                apps = listOf(
                    UsageRawApp("com.termux", "Termux", (scale * 0.55 * HOUR_MS * 7).toLong()),
                    UsageRawApp("com.google.android.youtube", "YouTube", (scale * 0.30 * HOUR_MS * 7).toLong()),
                    UsageRawApp("org.mozilla.firefox", "Firefox", (scale * 0.15 * HOUR_MS * 7).toLong()),
                ),
                pickups = 80 + i * 3,
            )
        }
    }

    private fun hourWeight(hour: Int): Float = when (hour) {
        in 0..6 -> 0f
        7, 8 -> 0.25f
        in 9..11 -> 0.85f
        12 -> 0.35f
        in 13..17 -> 1f
        18, 19 -> 0.55f
        20, 21 -> 0.9f
        else -> 0.2f
    }

    private fun sampleApps(weight: Float): List<UsageRawApp> {
        val unit = (weight * 20 * 60_000L).toLong()
        return listOf(
            UsageRawApp("com.termux", "Termux", unit * 3),
            UsageRawApp("com.google.android.youtube", "YouTube", unit),
            UsageRawApp("org.mozilla.firefox", "Firefox", (unit * 0.5).toLong()),
            UsageRawApp("com.slack", "Slack", (unit * 0.7).toLong()),
            UsageRawApp("com.google.android.gm", "Gmail", (unit * 0.3).toLong()),
            UsageRawApp("com.instagram.android", "Instagram", (unit * 0.15).toLong()),
        ).filter { it.millis >= MIN_MS }
    }

    private fun aggregateApps(
        rawDays: List<UsageRawDay>,
        overrides: Map<String, UsageKind>,
    ): List<UsageApp> {
        val totals = linkedMapOf<String, UsageRawApp>()
        rawDays.forEach { day ->
            day.apps.forEach { app ->
                if (app.millis < MIN_MS || isNoise(app.packageName)) return@forEach
                val prev = totals[app.packageName]
                totals[app.packageName] = if (prev == null) {
                    app
                } else {
                    prev.copy(millis = prev.millis + app.millis, label = prev.label.ifBlank { app.label })
                }
            }
        }
        return totals.values
            .map {
                UsageApp(
                    packageName = it.packageName,
                    label = it.label.ifBlank { it.packageName.substringAfterLast('.') },
                    millis = it.millis,
                    kind = kindOf(it.packageName, overrides),
                )
            }
            .sortedByDescending { it.millis }
            .take(TOP_APPS)
    }

    private val DISTRACTING = listOf(
        "youtube",
        "tiktok",
        "instagram",
        "facebook",
        "fb.katana",
        "twitter",
        "com.x.android",
        "reddit",
        "snapchat",
        "netflix",
        "disney",
        "hulu",
        "twitch",
        "tinder",
        "bumble",
        "candycrush",
        "game.casual",
        "king.com",
    )

    private val PRODUCTIVE = listOf(
        "termux",
        "calendar",
        "android.gm",
        "apps.docs",
        "apps.sheets",
        "apps.slides",
        "keep",
        "notion",
        "obsidian",
        "slack",
        "teams",
        "zoom",
        "maps",
        "authenticator",
        "fdroid",
        "github",
        "gitlab",
        "office",
        "outlook",
        "org.wikipedia",
    )
}

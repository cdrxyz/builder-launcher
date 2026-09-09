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
    W1("1W"),
    M1("1M"),
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
    val apps: List<UsageApp> = emptyList(),
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
    val vsLastWeekMs: Long?,
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
        if (deltaMs == null) return ""
        return when {
            deltaMs > 0 -> "${formatDuration(deltaMs)} more than last week"
            deltaMs < 0 -> "${formatDuration(-deltaMs)} less than last week"
            else -> "Same as last week"
        }
    }

    fun previousRange(todayStart: Long, period: UsagePeriod): Pair<Long, Long>? {
        return when (period) {
            UsagePeriod.W1 -> {
                val currentStart = todayStart - (DAYS - 1) * DAY_MS
                (currentStart - WEEK_MS) to (currentStart - 1)
            }
            UsagePeriod.M1 -> null
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
            UsagePeriod.W1 -> true
            UsagePeriod.M1 -> index == 0 || index == last || index % 5 == 0
        }
    }

    fun barLabel(startMs: Long, period: UsagePeriod, zone: ZoneId): String {
        val instant = Instant.ofEpochMilli(startMs)
        return when (period) {
            UsagePeriod.W1 -> DateTimeFormatter.ofPattern("EEE", Locale.US).withZone(zone).format(instant)
            UsagePeriod.M1 -> DateTimeFormatter.ofPattern("d", Locale.US).withZone(zone).format(instant)
        }
    }

    fun barDetail(startMs: Long, period: UsagePeriod, zone: ZoneId): String {
        val instant = Instant.ofEpochMilli(startMs)
        val pattern = when (period) {
            UsagePeriod.W1, UsagePeriod.M1 -> "EEE d MMM"
        }
        return DateTimeFormatter.ofPattern(pattern, Locale.US).withZone(zone).format(instant)
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
                apps = aggregateApps(listOf(raw), overrides),
            )
        }
        val apps = aggregateApps(rawDays, overrides)
        val total = bars.sumOf { it.totalMs }
        val productive = bars.sumOf { it.productiveMs }
        val distracting = bars.sumOf { it.distractingMs }
        val other = bars.sumOf { it.otherMs }
        val vs = if (period == UsagePeriod.W1) previousMs?.let { total - it } else null
        return UsageSnapshot(
            granted = granted,
            period = period,
            totalMs = total,
            productiveMs = productive,
            distractingMs = distracting,
            otherMs = other,
            vsLastWeekMs = vs,
            pickups = rawDays.sumOf { it.pickups },
            bars = bars,
            apps = apps,
        )
    }

    fun sample(period: UsagePeriod = UsagePeriod.W1): UsageSnapshot {
        val zone = ZoneOffset.UTC
        val dayStart = 1_725_667_200_000L
        return when (period) {
            UsagePeriod.W1 -> build(
                rawDays = sampleDays(dayStart, DAYS),
                overrides = emptyMap(),
                period = period,
                granted = true,
                zone = zone,
                previousMs = 18 * HOUR_MS,
            )
            UsagePeriod.M1 -> build(sampleDays(dayStart - DAY_MS * 23, MONTH_DAYS), emptyMap(), period, true, zone)
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

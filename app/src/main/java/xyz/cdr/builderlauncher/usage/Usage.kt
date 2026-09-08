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

enum class UsagePeriod { TODAY, WEEK }

data class UsageApp(
    val packageName: String,
    val label: String,
    val millis: Long,
    val kind: UsageKind,
)

data class UsageDay(
    val startMs: Long,
    val weekday: String,
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
    val days: List<UsageDay>,
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
    const val TOP_APPS = 12
    const val MIN_MS = 1_000L

    fun formatDuration(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val hours = total / 3_600_000L
        val minutes = (total % 3_600_000L) / 60_000L
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

    fun weekday(startMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        return DateTimeFormatter.ofPattern("EEEEE", Locale.US)
            .withZone(zone)
            .format(Instant.ofEpochMilli(startMs))
    }

    fun build(
        rawDays: List<UsageRawDay>,
        overrides: Map<String, UsageKind>,
        period: UsagePeriod,
        granted: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): UsageSnapshot {
        val days = rawDays.map { raw ->
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
            UsageDay(
                startMs = raw.startMs,
                weekday = weekday(raw.startMs, zone),
                productiveMs = productive,
                distractingMs = distracting,
                otherMs = other,
            )
        }
        val today = days.lastOrNull()
        val yesterday = days.getOrNull(days.size - 2)
        val window = when (period) {
            UsagePeriod.TODAY -> days.takeLast(1)
            UsagePeriod.WEEK -> days
        }
        val apps = aggregateApps(
            rawDays = when (period) {
                UsagePeriod.TODAY -> rawDays.takeLast(1)
                UsagePeriod.WEEK -> rawDays
            },
            overrides = overrides,
        )
        val total = window.sumOf { it.totalMs }
        val productive = window.sumOf { it.productiveMs }
        val distracting = window.sumOf { it.distractingMs }
        val other = window.sumOf { it.otherMs }
        val vs = if (period == UsagePeriod.TODAY && today != null && yesterday != null) {
            today.totalMs - yesterday.totalMs
        } else {
            null
        }
        return UsageSnapshot(
            granted = granted,
            period = period,
            totalMs = total,
            productiveMs = productive,
            distractingMs = distracting,
            otherMs = other,
            vsYesterdayMs = vs,
            pickups = if (period == UsagePeriod.TODAY) rawDays.lastOrNull()?.pickups ?: 0 else rawDays.sumOf { it.pickups },
            days = days,
            apps = apps,
        )
    }

    fun sample(): UsageSnapshot {
        val day = 86_400_000L
        val start = 1_725_667_200_000L
        val raw = listOf(
            UsageRawDay(
                startMs = start,
                apps = listOf(
                    UsageRawApp("com.termux", "Termux", 2 * 3_600_000L),
                    UsageRawApp("com.google.android.youtube", "YouTube", 90 * 60_000L),
                    UsageRawApp("org.mozilla.firefox", "Firefox", 40 * 60_000L),
                ),
                pickups = 18,
            ),
            UsageRawDay(
                startMs = start + day,
                apps = listOf(
                    UsageRawApp("com.termux", "Termux", 3 * 3_600_000L),
                    UsageRawApp("com.google.android.youtube", "YouTube", 2 * 3_600_000L),
                    UsageRawApp("org.mozilla.firefox", "Firefox", 50 * 60_000L),
                ),
                pickups = 24,
            ),
            UsageRawDay(
                startMs = start + day * 2,
                apps = listOf(
                    UsageRawApp("com.termux", "Termux", 80 * 60_000L),
                    UsageRawApp("com.google.android.youtube", "YouTube", 3 * 3_600_000L),
                    UsageRawApp("com.instagram.android", "Instagram", 70 * 60_000L),
                ),
                pickups = 31,
            ),
            UsageRawDay(
                startMs = start + day * 3,
                apps = listOf(
                    UsageRawApp("com.termux", "Termux", 4 * 3_600_000L),
                    UsageRawApp("com.google.android.gm", "Gmail", 45 * 60_000L),
                    UsageRawApp("org.mozilla.firefox", "Firefox", 55 * 60_000L),
                ),
                pickups = 16,
            ),
            UsageRawDay(
                startMs = start + day * 4,
                apps = listOf(
                    UsageRawApp("com.termux", "Termux", 90 * 60_000L),
                    UsageRawApp("com.google.android.youtube", "YouTube", 80 * 60_000L),
                    UsageRawApp("org.mozilla.firefox", "Firefox", 2 * 3_600_000L),
                ),
                pickups = 22,
            ),
            UsageRawDay(
                startMs = start + day * 5,
                apps = listOf(
                    UsageRawApp("com.termux", "Termux", 2 * 3_600_000L + 20 * 60_000L),
                    UsageRawApp("com.google.android.youtube", "YouTube", 50 * 60_000L),
                    UsageRawApp("com.instagram.android", "Instagram", 25 * 60_000L),
                ),
                pickups = 19,
            ),
            UsageRawDay(
                startMs = start + day * 6,
                apps = listOf(
                    UsageRawApp("com.termux", "Termux", 3 * 3_600_000L + 12 * 60_000L),
                    UsageRawApp("com.google.android.youtube", "YouTube", 1 * 3_600_000L + 18 * 60_000L),
                    UsageRawApp("org.mozilla.firefox", "Firefox", 42 * 60_000L),
                    UsageRawApp("com.google.android.gm", "Gmail", 28 * 60_000L),
                    UsageRawApp("com.instagram.android", "Instagram", 11 * 60_000L),
                    UsageRawApp("com.slack", "Slack", 54 * 60_000L),
                ),
                pickups = 27,
            ),
        )
        return build(raw, emptyMap(), UsagePeriod.TODAY, granted = true, zone = ZoneOffset.UTC)
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

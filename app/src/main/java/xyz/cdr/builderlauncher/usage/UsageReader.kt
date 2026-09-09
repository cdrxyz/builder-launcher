package xyz.cdr.builderlauncher.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId

class UsageReader(private val context: Context) {
    fun granted(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun load(
        store: UsageStore,
        period: UsagePeriod,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): UsageSnapshot {
        if (!granted()) {
            return Usage.build(emptyList(), emptyMap(), period, granted = false, zone = zone)
        }
        val usm = context.getSystemService(UsageStatsManager::class.java)
            ?: return Usage.build(emptyList(), emptyMap(), period, granted = false, zone = zone)
        val today = InstantDay.start(nowMs, zone)
        val overrides = store.overrides()
        return when (period) {
            UsagePeriod.W1 -> {
                val previous = Usage.previousRange(today, period)?.let { (start, end) ->
                    readBucket(usm, start, end)
                }
                Usage.build(
                    rawDays = buckets(usm, today, Usage.DAYS, Usage.DAY_MS, nowMs),
                    overrides = overrides,
                    period = period,
                    granted = true,
                    zone = zone,
                    previousMs = previous?.let { Usage.totalOf(it) },
                )
            }
            UsagePeriod.M1 -> Usage.build(
                rawDays = buckets(usm, today, Usage.MONTH_DAYS, Usage.DAY_MS, nowMs),
                overrides = overrides,
                period = period,
                granted = true,
                zone = zone,
            )
        }
    }

    fun loadToday(
        store: UsageStore,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): UsageToday {
        if (!granted()) return Usage.todayOf(UsageRawDay(0L, emptyList()), emptyMap(), granted = false)
        val usm = context.getSystemService(UsageStatsManager::class.java)
            ?: return Usage.todayOf(UsageRawDay(0L, emptyList()), emptyMap(), granted = false)
        val start = InstantDay.start(nowMs, zone)
        val end = nowMs.coerceAtLeast(start)
        return Usage.todayOf(readBucket(usm, start, end), store.overrides(), granted = true)
    }

    private fun buckets(
        usm: UsageStatsManager,
        todayStart: Long,
        count: Int,
        stepMs: Long,
        nowMs: Long,
    ): List<UsageRawDay> {
        return (count - 1 downTo 0).map { offset ->
            val start = todayStart - offset * stepMs
            val end = (start + stepMs - 1).coerceAtMost(nowMs)
            if (start > nowMs) UsageRawDay(start, emptyList()) else readBucket(usm, start, end)
        }
    }

    private fun readBucket(usm: UsageStatsManager, start: Long, end: Long): UsageRawDay {
        if (end < start) return UsageRawDay(start, emptyList())
        val stats = runCatching { usm.queryAndAggregateUsageStats(start, end) }.getOrDefault(emptyMap())
        val apps = stats.values.mapNotNull { row ->
            val pkg = row.packageName ?: return@mapNotNull null
            val ms = row.totalTimeInForeground
            if (ms < Usage.MIN_MS) return@mapNotNull null
            UsageRawApp(packageName = pkg, label = labelOf(pkg), millis = ms)
        }
        return UsageRawDay(startMs = start, apps = apps, pickups = pickups(usm, start, end))
    }

    private fun pickups(usm: UsageStatsManager, start: Long, end: Long): Int {
        val events = runCatching { usm.queryEvents(start, end) }.getOrNull() ?: return 0
        val event = UsageEvents.Event()
        var n = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) n++
        }
        return n
    }

    private fun labelOf(packageName: String): String {
        return runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrElse { packageName.substringAfterLast('.') }
    }

    private object InstantDay {
        fun start(nowMs: Long, zone: ZoneId): Long {
            return LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        }
    }
}

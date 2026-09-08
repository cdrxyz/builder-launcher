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
            return Usage.build(emptyList(), emptyMap(), period, granted = false)
        }
        val usm = context.getSystemService(UsageStatsManager::class.java)
            ?: return Usage.build(emptyList(), emptyMap(), period, granted = false)
        val today = InstantDay.start(nowMs, zone)
        val days = (Usage.DAYS - 1 downTo 0).map { offset ->
            val start = today - offset * DAY_MS
            val end = (start + DAY_MS - 1).coerceAtMost(nowMs)
            readDay(usm, start, end)
        }
        return Usage.build(days, store.overrides(), period, granted = true)
    }

    private fun readDay(usm: UsageStatsManager, start: Long, end: Long): UsageRawDay {
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

    companion object {
        private const val DAY_MS = 86_400_000L
    }
}

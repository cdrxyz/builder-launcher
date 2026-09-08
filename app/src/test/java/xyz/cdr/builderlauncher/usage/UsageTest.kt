package xyz.cdr.builderlauncher.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class UsageTest {
    @Test
    fun formatDuration() {
        assertEquals("0m", Usage.formatDuration(0))
        assertEquals("12s", Usage.formatDuration(12_000))
        assertEquals("5m", Usage.formatDuration(5 * 60_000L))
        assertEquals("2h", Usage.formatDuration(2 * 3_600_000L))
        assertEquals("2h 14m", Usage.formatDuration(2 * 3_600_000L + 14 * 60_000L))
    }

    @Test
    fun percentAndVs() {
        assertEquals(0, Usage.percent(10, 0))
        assertEquals(25, Usage.percent(15, 60))
        assertEquals("Same as yesterday", Usage.vsLabel(0))
        assertEquals("1h more than yesterday", Usage.vsLabel(3_600_000L))
        assertEquals("20m less than yesterday", Usage.vsLabel(-20 * 60_000L))
        assertEquals("No yesterday yet", Usage.vsLabel(null))
    }

    @Test
    fun defaultKinds() {
        assertEquals(UsageKind.PRODUCTIVE, Usage.defaultKind("com.termux"))
        assertEquals(UsageKind.PRODUCTIVE, Usage.defaultKind("com.slack"))
        assertEquals(UsageKind.DISTRACTING, Usage.defaultKind("com.google.android.youtube"))
        assertEquals(UsageKind.DISTRACTING, Usage.defaultKind("com.instagram.android"))
        assertEquals(UsageKind.OTHER, Usage.defaultKind("org.mozilla.firefox"))
        assertEquals(UsageKind.OTHER, Usage.defaultKind("xyz.cdr.builderlauncher"))
    }

    @Test
    fun cycleKind() {
        assertEquals(UsageKind.PRODUCTIVE, UsageKind.OTHER.next())
        assertEquals(UsageKind.DISTRACTING, UsageKind.PRODUCTIVE.next())
        assertEquals(UsageKind.OTHER, UsageKind.DISTRACTING.next())
    }

    @Test
    fun buildSplitsTodayAndMarksOverrides() {
        val day = 86_400_000L
        val start = 1_725_667_200_000L
        val raw = listOf(
            UsageRawDay(
                startMs = start,
                apps = listOf(UsageRawApp("com.termux", "Termux", 2 * 3_600_000L)),
                pickups = 10,
            ),
            UsageRawDay(
                startMs = start + day,
                apps = listOf(
                    UsageRawApp("com.termux", "Termux", 3_600_000L),
                    UsageRawApp("com.google.android.youtube", "YouTube", 3_600_000L),
                    UsageRawApp("org.mozilla.firefox", "Firefox", 30 * 60_000L),
                    UsageRawApp("com.android.systemui", "System", 9_000_000L),
                ),
                pickups = 21,
            ),
        )
        val today = Usage.build(
            rawDays = raw,
            overrides = mapOf("org.mozilla.firefox" to UsageKind.PRODUCTIVE),
            period = UsagePeriod.TODAY,
            granted = true,
            zone = ZoneOffset.UTC,
        )
        assertEquals(2 * 3_600_000L + 30 * 60_000L, today.totalMs)
        assertEquals(3_600_000L + 30 * 60_000L, today.productiveMs)
        assertEquals(3_600_000L, today.distractingMs)
        assertEquals(0L, today.otherMs)
        assertEquals(21, today.pickups)
        assertEquals(1_800_000L, today.vsYesterdayMs)
        assertTrue(today.apps.none { it.packageName == "com.android.systemui" })
        assertEquals(UsageKind.PRODUCTIVE, today.apps.find { it.packageName == "org.mozilla.firefox" }?.kind)

        val week = Usage.build(raw, emptyMap(), UsagePeriod.WEEK, granted = true, zone = ZoneOffset.UTC)
        assertEquals(raw.sumOf { it.apps.filterNot { app -> Usage.isNoise(app.packageName) }.sumOf { app -> app.millis } }, week.totalMs)
        assertEquals(31, week.pickups)
    }

    @Test
    fun sampleIsGrantedToday() {
        val snap = Usage.sample()
        assertTrue(snap.granted)
        assertEquals(UsagePeriod.TODAY, snap.period)
        assertTrue(snap.totalMs > 0)
        assertEquals(7, snap.days.size)
        assertTrue(snap.apps.isNotEmpty())
    }
}

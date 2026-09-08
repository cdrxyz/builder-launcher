package xyz.cdr.builderlauncher.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun indexAtHitsBarSlots() {
        assertEquals(0, Usage.indexAt(-10f, 100f, 10))
        assertEquals(0, Usage.indexAt(0f, 100f, 10))
        assertEquals(4, Usage.indexAt(45f, 100f, 10))
        assertEquals(9, Usage.indexAt(99f, 100f, 10))
        assertEquals(9, Usage.indexAt(140f, 100f, 10))
        assertEquals(0, Usage.indexAt(10f, 0f, 10))
    }

    @Test
    fun periodLabels() {
        assertEquals(listOf("today", "7D", "30D", "6M"), UsagePeriod.entries.map { it.label })
    }

    @Test
    fun buildSplitsTodayAndMarksOverrides() {
        val hour = Usage.HOUR_MS
        val start = 1_725_667_200_000L
        val raw = (0 until 24).map { h ->
            if (h != 14) {
                UsageRawDay(start + h * hour, emptyList())
            } else {
                UsageRawDay(
                    startMs = start + h * hour,
                    apps = listOf(
                        UsageRawApp("com.termux", "Termux", 3_600_000L),
                        UsageRawApp("com.google.android.youtube", "YouTube", 3_600_000L),
                        UsageRawApp("org.mozilla.firefox", "Firefox", 30 * 60_000L),
                        UsageRawApp("com.android.systemui", "System", 9_000_000L),
                    ),
                    pickups = 21,
                )
            }
        }
        val today = Usage.build(
            rawDays = raw,
            overrides = mapOf("org.mozilla.firefox" to UsageKind.PRODUCTIVE),
            period = UsagePeriod.TODAY,
            granted = true,
            zone = ZoneOffset.UTC,
            previousMs = 2 * 3_600_000L,
        )
        assertEquals(24, today.bars.size)
        assertEquals(2 * 3_600_000L + 30 * 60_000L, today.totalMs)
        assertEquals(3_600_000L + 30 * 60_000L, today.productiveMs)
        assertEquals(3_600_000L, today.distractingMs)
        assertEquals(0L, today.otherMs)
        assertEquals(21, today.pickups)
        assertEquals(30 * 60_000L, today.vsYesterdayMs)
        assertTrue(today.apps.none { it.packageName == "com.android.systemui" })
        assertEquals(UsageKind.PRODUCTIVE, today.apps.find { it.packageName == "org.mozilla.firefox" }?.kind)
        assertEquals("14", today.bars[14].label)
        assertTrue(today.bars[14].detail.contains("14:00"))
    }

    @Test
    fun weekMonthAndHalfBarCounts() {
        val start = 1_725_667_200_000L
        val weekRaw = (0 until 7).map { i ->
            UsageRawDay(start + i * Usage.DAY_MS, listOf(UsageRawApp("com.termux", "Termux", Usage.HOUR_MS)))
        }
        val week = Usage.build(weekRaw, emptyMap(), UsagePeriod.WEEK, true, ZoneOffset.UTC)
        assertEquals(7, week.bars.size)
        assertEquals(7 * Usage.HOUR_MS, week.totalMs)
        assertNull(week.vsYesterdayMs)

        val month = Usage.sample(UsagePeriod.MONTH)
        assertEquals(30, month.bars.size)
        val half = Usage.sample(UsagePeriod.HALF)
        assertEquals(26, half.bars.size)
    }

    @Test
    fun sampleIsGrantedTodayHourly() {
        val snap = Usage.sample()
        assertTrue(snap.granted)
        assertEquals(UsagePeriod.TODAY, snap.period)
        assertEquals(24, snap.bars.size)
        assertTrue(snap.totalMs > 0)
        assertTrue(snap.apps.isNotEmpty())
        assertTrue(Usage.axisLabel(0, 24, UsagePeriod.TODAY))
        assertTrue(Usage.axisLabel(6, 24, UsagePeriod.TODAY))
        assertTrue(!Usage.axisLabel(1, 24, UsagePeriod.TODAY))
    }
}

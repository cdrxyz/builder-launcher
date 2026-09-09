package xyz.cdr.builderlauncher.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class UsageTest {
    @Test
    fun backFacesHome() {
        assertEquals("<", Usage.BACK)
    }

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
        assertEquals("Same as last week", Usage.vsLabel(0))
        assertEquals("1h more than last week", Usage.vsLabel(3_600_000L))
        assertEquals("20m less than last week", Usage.vsLabel(-20 * 60_000L))
        assertEquals("", Usage.vsLabel(null))
    }

    @Test
    fun pinCaptionIsMinutesThenShare() {
        assertEquals("30m", Usage.pinDuration(30 * 60_000L))
        assertEquals("17%", Usage.pinShare(30 * 60_000L, 180 * 60_000L))
        assertEquals("0m", Usage.pinDuration(0))
        assertEquals("0%", Usage.pinShare(0, 0))
        assertEquals("0m", Usage.pinDuration(12_000))
        assertEquals("0%", Usage.pinShare(12_000, 180 * 60_000L))
        assertEquals("120m", Usage.pinDuration(2 * 3_600_000L))
        assertEquals("50%", Usage.pinShare(2 * 3_600_000L, 4 * 3_600_000L))
    }

    @Test
    fun todayMarksPinsByKind() {
        val today = Usage.todayOf(
            UsageRawDay(
                startMs = 1L,
                apps = listOf(
                    UsageRawApp("com.termux", "Termux", 30 * 60_000L),
                    UsageRawApp("com.google.android.youtube", "YouTube", 90 * 60_000L),
                    UsageRawApp("org.mozilla.firefox", "Firefox", 60 * 60_000L),
                    UsageRawApp("com.android.systemui", "System", 9_000_000L),
                ),
            ),
            overrides = mapOf("org.mozilla.firefox" to UsageKind.PRODUCTIVE),
            granted = true,
        )
        assertEquals(180 * 60_000L, today.totalMs)
        assertEquals("30m", today.mark("com.termux")?.duration)
        assertEquals("17%", today.mark("com.termux")?.share)
        assertEquals(true, today.mark("com.termux")?.productive)
        assertEquals("90m", today.mark("com.google.android.youtube")?.duration)
        assertEquals("50%", today.mark("com.google.android.youtube")?.share)
        assertEquals(false, today.mark("com.google.android.youtube")?.productive)
        assertEquals(true, today.mark("org.mozilla.firefox")?.productive)
        assertEquals("0m", today.mark("com.android.camera")?.duration)
        assertEquals("0%", today.mark("com.android.camera")?.share)
        assertEquals(false, today.mark("com.android.camera")?.productive)
        assertEquals(null, Usage.todayOf(UsageRawDay(0, emptyList()), emptyMap(), granted = false).mark("com.termux"))
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
    fun periodLabelsAreWeekAndMonth() {
        assertEquals(listOf("1W", "1M"), UsagePeriod.entries.map { it.label })
        assertEquals(UsagePeriod.W1, UsagePeriod.entries.first())
    }

    @Test
    fun buildSplitsWeekDaysAndMarksOverrides() {
        val start = 1_725_667_200_000L
        val raw = (0 until 7).map { d ->
            if (d != 3) {
                UsageRawDay(start + d * Usage.DAY_MS, emptyList())
            } else {
                UsageRawDay(
                    startMs = start + d * Usage.DAY_MS,
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
        val week = Usage.build(
            rawDays = raw,
            overrides = mapOf("org.mozilla.firefox" to UsageKind.PRODUCTIVE),
            period = UsagePeriod.W1,
            granted = true,
            zone = ZoneOffset.UTC,
            previousMs = 2 * 3_600_000L,
        )
        assertEquals(7, week.bars.size)
        assertEquals(2 * 3_600_000L + 30 * 60_000L, week.totalMs)
        assertEquals(3_600_000L + 30 * 60_000L, week.productiveMs)
        assertEquals(3_600_000L, week.distractingMs)
        assertEquals(0L, week.otherMs)
        assertEquals(21, week.pickups)
        assertEquals(30 * 60_000L, week.vsLastWeekMs)
        assertTrue(week.apps.none { it.packageName == "com.android.systemui" })
        assertEquals(UsageKind.PRODUCTIVE, week.apps.find { it.packageName == "org.mozilla.firefox" }?.kind)
        assertEquals("Tue", week.bars[3].label)
        assertTrue(week.bars[3].detail.contains("Tue"))
        assertEquals(
            listOf("com.termux", "com.google.android.youtube", "org.mozilla.firefox"),
            week.bars[3].apps.map { it.packageName },
        )
        assertEquals(3_600_000L, week.bars[3].apps.find { it.packageName == "com.termux" }?.millis)
        assertTrue(week.bars[0].apps.isEmpty())
        assertEquals(week.apps.map { it.packageName }, week.bars[3].apps.map { it.packageName })
    }

    @Test
    fun monthBarCountHasNoWeekDelta() {
        val start = 1_725_667_200_000L
        val monthRaw = (0 until 30).map { i ->
            UsageRawDay(start + i * Usage.DAY_MS, listOf(UsageRawApp("com.termux", "Termux", Usage.HOUR_MS)))
        }
        val month = Usage.build(monthRaw, emptyMap(), UsagePeriod.M1, true, ZoneOffset.UTC)
        assertEquals(30, month.bars.size)
        assertEquals(30 * Usage.HOUR_MS, month.totalMs)
        assertNull(month.vsLastWeekMs)
    }

    @Test
    fun previousWeekDoesNotOverlapCurrentBars() {
        val today = 1_725_667_200_000L
        val currentStart = today - (Usage.DAYS - 1) * Usage.DAY_MS
        val range = Usage.previousRange(today, UsagePeriod.W1)!!
        assertEquals(currentStart - Usage.WEEK_MS, range.first)
        assertEquals(currentStart - 1, range.second)
        assertTrue(range.second < currentStart)
        assertEquals(Usage.WEEK_MS - 1, range.second - range.first)
        assertNull(Usage.previousRange(today, UsagePeriod.M1))
    }

    @Test
    fun sampleIsGrantedWeekDaily() {
        val snap = Usage.sample()
        assertTrue(snap.granted)
        assertEquals(UsagePeriod.W1, snap.period)
        assertEquals(7, snap.bars.size)
        assertTrue(snap.totalMs > 0)
        assertTrue(snap.apps.isNotEmpty())
        assertTrue(snap.bars[3].apps.isNotEmpty())
        assertTrue(snap.bars[3].apps.map { it.packageName } != snap.apps.map { it.packageName })
        assertTrue(Usage.axisLabel(0, 7, UsagePeriod.W1))
        assertTrue(Usage.axisLabel(3, 7, UsagePeriod.W1))
        assertTrue(Usage.axisLabel(1, 30, UsagePeriod.M1) == false)
        assertTrue(Usage.axisLabel(0, 30, UsagePeriod.M1))
    }
}

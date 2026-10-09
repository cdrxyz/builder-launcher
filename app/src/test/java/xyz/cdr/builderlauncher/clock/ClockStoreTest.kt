package xyz.cdr.builderlauncher.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ClockStoreTest {
    private fun store(): Pair<File, ClockStore> {
        val dir = createTempDirectory("clock-store").toFile()
        val file = File(dir, "clock.json")
        return file to ClockStore(file)
    }

    @Test
    fun alarmsTimersAndZonesSurviveRelaunch() {
        val (file, first) = store()
        first.setTimer(
            TimerState(
                durationMs = 90_000,
                remainingMs = 45_000,
                running = true,
                endsAt = 9_000_000L,
            ),
        )
        first.addAlarm(7, 30, "up")
        first.addAlarm(22, 30, "Take out garbage", setOf(3))
        first.addZone("London", "Europe/London")
        first.addZone("Tokyo", "Asia/Tokyo")

        val relaunched = ClockStore(file)
        val snap = relaunched.snapshot()
        assertEquals(90_000L, snap.timer.durationMs)
        assertEquals(45_000L, snap.timer.remainingMs)
        assertTrue(snap.timer.running)
        assertEquals(9_000_000L, snap.timer.endsAt)
        assertEquals(listOf(7 to 30, 22 to 30), snap.alarms.map { it.hour to it.minute })
        assertEquals(listOf("up", "Take out garbage"), snap.alarms.map { it.label })
        assertEquals(setOf(3), snap.alarms.last().days)
        assertTrue(snap.alarms.all { it.enabled })
        assertEquals(
            listOf("London" to "Europe/London", "Tokyo" to "Asia/Tokyo"),
            snap.zones.map { it.label to it.zoneId },
        )
        assertNull(snap.alert)
    }

    @Test
    fun corruptFileDoesNotWipeLaterWrites() {
        val (file, first) = store()
        first.addAlarm(6, 0)
        file.writeText("{not json")
        val relaunched = ClockStore(file)
        assertTrue(relaunched.snapshot().alarms.isEmpty())
        assertTrue(file.readText().contains("{not json"))
        relaunched.addZone("UTC", "UTC")
        val again = ClockStore(file)
        assertEquals(listOf("UTC"), again.snapshot().zones.map { it.label })
        assertTrue(again.snapshot().alarms.isEmpty())
    }

    @Test
    fun removeAlarmStaysGoneAfterRelaunch() {
        val (file, _) = store()
        file.writeText(
            """
            {
              "alarms": [
                { "id": "a-gone", "hour": 7, "minute": 30, "label": "up" },
                { "id": "a-keep", "hour": 8, "minute": 0, "label": "keep" }
              ]
            }
            """.trimIndent(),
        )
        val first = ClockStore(file)
        first.removeAlarm("a-gone")
        val again = ClockStore(file)
        assertEquals(listOf("a-keep"), again.snapshot().alarms.map { it.id })
        assertTrue(again.snapshot().deletedAlarmIds.contains("a-gone"))
    }

    @Test
    fun unknownJsonKeysAreIgnored() {
        val (file, _) = store()
        file.writeText(
            """
            {
              "timer": { "durationMs": 60000, "remainingMs": 60000, "running": false },
              "alarms": [{ "id": "a1", "hour": 8, "minute": 15, "enabled": false, "extra": true }],
              "zones": [{ "id": "z1", "label": "NYC", "zoneId": "America/New_York", "weather": "nope" }],
              "future": 1
            }
            """.trimIndent(),
        )
        val snap = ClockStore(file).snapshot()
        assertEquals(60_000L, snap.timer.durationMs)
        assertFalse(snap.timer.running)
        assertEquals(8, snap.alarms.single().hour)
        assertFalse(snap.alarms.single().enabled)
        assertEquals("NYC", snap.zones.single().label)
        assertEquals("America/New_York", snap.zones.single().zoneId)
    }

    @Test
    fun namedTimersSurviveRelaunch() {
        val (file, first) = store()
        first.replaceTimers(
            listOf(
                TimerState(
                    id = "rice",
                    durationMs = 10 * 60_000L,
                    remainingMs = 9 * 60_000L,
                    running = true,
                    endsAt = 9_000_000L,
                    label = "Rice",
                ),
                TimerState(
                    id = "pasta",
                    durationMs = 8 * 60_000L,
                    remainingMs = 8 * 60_000L,
                    label = "Pasta",
                ),
            ),
        )
        val relaunched = ClockStore(file)
        val snap = Clock.normalize(relaunched.snapshot())
        assertEquals(listOf("Rice", "Pasta"), snap.timers.map { it.label })
        assertTrue(snap.timers.first().running)
        assertEquals(9_000_000L, snap.timers.first().endsAt)
        assertEquals("Rice", snap.timer.label)
    }

    @Test
    fun addTimerKeepsTheRunningOne() {
        val (_, store) = store()
        store.setTimer(
            Clock.start(Clock.setDuration(TimerState(id = "rice"), 10 * 60_000L, "Rice"), 1_000_000L),
        )
        store.addTimer(Clock.parseTimerInput("Pasta 8 minutes")!!)
        val snap = Clock.normalize(store.snapshot())
        assertEquals(2, snap.timers.size)
        assertTrue(snap.timers.any { it.label == "Rice" && it.running })
        assertTrue(snap.timers.any { it.label == "Pasta" && !it.running && it.durationMs == 8 * 60_000L })
    }

    @Test
    fun addTimerKeepsAPausedOne() {
        val (_, store) = store()
        val now = 1_000_000L
        store.setTimer(
            Clock.pause(
                Clock.start(Clock.setDuration(TimerState(id = "tea"), 10 * 60_000L, "Tea"), now),
                now + 30_000L,
            ),
        )
        store.addTimer(ParsedTimer(15 * 60_000L))
        val snap = Clock.normalize(store.snapshot())
        assertEquals(2, snap.timers.size)
        assertEquals("Tea", snap.timers.first().label)
        assertFalse(snap.timers.first().running)
        assertEquals(15 * 60_000L, snap.timers.last().durationMs)
    }

    @Test
    fun addUnlabeledFiveMinuteKeepsExistingTimer() {
        val (_, store) = store()
        store.setTimer(
            Clock.start(Clock.setDuration(TimerState(id = "rice"), 10 * 60_000L, "Rice"), 1_000_000L),
        )
        store.addTimer(Clock.parseTimerInput("5")!!)
        val snap = Clock.normalize(store.snapshot())
        assertEquals(2, snap.timers.size)
        assertTrue(snap.timers.any { it.label == "Rice" && it.running })
        assertTrue(snap.timers.any { it.label.isBlank() && it.durationMs == 5 * 60_000L && !it.running })
    }

    @Test
    fun backupApplyKeepsRunningTimers() {
        val (_, store) = store()
        val running = Clock.start(Clock.setDuration(TimerState(id = "rice"), 10 * 60_000L, "Rice"), 1_000_000L)
        store.replaceTimers(listOf(running))
        store.replaceFromBackup(
            Clock.keepLocalTimers(
                store.snapshot(),
                ClockSnapshot(
                    alarms = listOf(ClockAlarm(id = "a1", hour = 7, minute = 30)),
                    zones = emptyList(),
                ),
            ),
        )
        val snap = Clock.normalize(store.snapshot())
        assertEquals("Rice", snap.timers.single().label)
        assertTrue(snap.timers.single().running)
        assertEquals(1, snap.alarms.size)
    }

    @Test
    fun intervalSeriesGetsDistinctIds() {
        val (_, store) = store()
        val parsed = Clock.parseAlarmInputs("Advil every 4 hours starting at 8pm")
        val added = store.addAlarms(parsed)
        assertEquals(6, added.size)
        assertEquals(6, added.map { it.id }.distinct().size)
        assertEquals(
            listOf(0 to 0, 4 to 0, 8 to 0, 12 to 0, 16 to 0, 20 to 0),
            store.snapshot().alarms.map { it.hour to it.minute },
        )
        assertTrue(store.snapshot().alarms.all { it.label == "Advil" && it.enabled })
    }
}

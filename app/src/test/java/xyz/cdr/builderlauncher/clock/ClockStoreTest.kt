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
        first.addZone("London", "Europe/London")
        first.addZone("Tokyo", "Asia/Tokyo")

        val relaunched = ClockStore(file)
        val snap = relaunched.snapshot()
        assertEquals(90_000L, snap.timer.durationMs)
        assertEquals(45_000L, snap.timer.remainingMs)
        assertTrue(snap.timer.running)
        assertEquals(9_000_000L, snap.timer.endsAt)
        assertEquals(listOf(7 to 30), snap.alarms.map { it.hour to it.minute })
        assertEquals("up", snap.alarms.single().label)
        assertTrue(snap.alarms.single().enabled)
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
}

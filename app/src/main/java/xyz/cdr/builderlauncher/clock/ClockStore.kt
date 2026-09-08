package xyz.cdr.builderlauncher.clock

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ClockStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "clock.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ClockSnapshot> = _state.asStateFlow()

    fun snapshot(): ClockSnapshot = _state.value

    fun setTimer(timer: TimerState) {
        persist(_state.value.copy(timer = timer))
    }

    fun addAlarm(hour: Int, minute: Int, label: String = ""): ClockAlarm {
        val alarm = ClockAlarm(
            id = System.currentTimeMillis().toString(36),
            hour = hour,
            minute = minute,
            label = label,
        )
        persist(_state.value.copy(alarms = (_state.value.alarms + alarm).sortedWith(compareBy({ it.hour }, { it.minute }))))
        return alarm
    }

    fun toggleAlarm(id: String) {
        persist(
            _state.value.copy(
                alarms = _state.value.alarms.map { if (it.id == id) it.copy(enabled = !it.enabled) else it },
            ),
        )
    }

    fun removeAlarm(id: String) {
        persist(_state.value.copy(alarms = _state.value.alarms.filterNot { it.id == id }))
    }

    fun addZone(label: String, zoneId: String): WorldClock? {
        val zone = runCatching { java.time.ZoneId.of(zoneId) }.getOrNull() ?: return null
        val existing = _state.value.zones
        if (existing.any { it.zoneId == zone.id }) return existing.find { it.zoneId == zone.id }
        val row = WorldClock(
            id = System.currentTimeMillis().toString(36),
            label = label,
            zoneId = zone.id,
        )
        persist(_state.value.copy(zones = existing + row))
        return row
    }

    fun removeZone(id: String) {
        persist(_state.value.copy(zones = _state.value.zones.filterNot { it.id == id }))
    }

    private fun persist(next: ClockSnapshot) {
        _state.value = next
        file.writeText(json.encodeToString(next))
    }

    private fun load(): ClockSnapshot {
        if (!file.exists()) return ClockSnapshot()
        return runCatching { json.decodeFromString<ClockSnapshot>(file.readText()) }.getOrDefault(ClockSnapshot())
    }
}

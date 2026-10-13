package xyz.cdr.builderlauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PinnedApps(context: Context) {
    private val prefs = context.getSharedPreferences("builder.pins", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _changes = MutableStateFlow(readChanges())
    private val _packages = MutableStateFlow(read())
    val changes: StateFlow<List<PinChange>> = _changes.asStateFlow()
    val packages: StateFlow<List<String>> = _packages.asStateFlow()

    fun packages(): List<String> = _packages.value

    fun pin(packageName: String, at: Long = System.currentTimeMillis()) {
        val (next, changes) = record(_packages.value, _changes.value, packageName, pinned = true, at = at)
        persist(next, changes)
    }

    fun unpin(packageName: String, at: Long = System.currentTimeMillis()) {
        val (next, changes) = record(_packages.value, _changes.value, packageName, pinned = false, at = at)
        persist(next, changes)
    }

    fun move(from: Int, to: Int) {
        persist(ListReorder.move(_packages.value, from, to), _changes.value)
    }

    fun moveVisible(visible: List<String>, from: Int, to: Int) {
        persist(orderAfterMove(_packages.value, visible, from, to), _changes.value)
    }

    fun isPinned(packageName: String): Boolean = packageName in _packages.value

    fun replaceAll(next: List<String>, changes: List<PinChange>) {
        persist(next, changes)
    }

    private fun persist(next: List<String>, changes: List<PinChange>) {
        _changes.value = changes
        _packages.value = next
        prefs.edit()
            .putString(KEY_PINS, next.joinToString("\n"))
            .putString(KEY_CHANGES, json.encodeToString(changes))
            .apply()
    }

    private fun read(): List<String> =
        prefs.getString(KEY_PINS, "")
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    private fun readChanges(): List<PinChange> {
        val raw = prefs.getString(KEY_CHANGES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<PinChange>>(raw) }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_PINS = "pins"
        private const val KEY_CHANGES = "pinChanges"

        fun record(
            pins: List<String>,
            changes: List<PinChange>,
            packageName: String,
            pinned: Boolean,
            at: Long,
        ): Pair<List<String>, List<PinChange>> {
            if (packageName.isBlank()) return pins to changes
            val nextPins = if (pinned) {
                (pins + packageName).distinct()
            } else {
                pins.filterNot { it == packageName }
            }
            val nextChanges = changes.filterNot { it.packageName == packageName } +
                PinChange(packageName, pinned, at)
            return nextPins to nextChanges
        }

        fun orderAfterMove(stored: List<String>, visible: List<String>, from: Int, to: Int): List<String> {
            val nextVisible = ListReorder.move(visible, from, to)
            if (nextVisible === visible) return stored
            val leftover = stored.filter { it !in nextVisible }
            return nextVisible + leftover
        }
    }
}

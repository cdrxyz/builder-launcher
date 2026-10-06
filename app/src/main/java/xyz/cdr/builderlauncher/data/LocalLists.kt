package xyz.cdr.builderlauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class LocalItem(
    val id: String,
    val kind: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val updatedAt: Long = 0,
    /** Manual rank. 0 means unset, so display uses createdAt. Higher is closer to the top. */
    val order: Long = 0,
    /** When order was last set by a drag or arrow. 0 means never manually placed. */
    val orderedAt: Long = 0,
) {
    val done: Boolean get() = completedAt != null
    val editedAt: Long get() = if (updatedAt > 0L) updatedAt else createdAt
}

class LocalLists internal constructor(
    private val file: File,
    private val deletedFile: File = File(file.parentFile ?: file, "deleted-ids.json"),
) {
    constructor(context: Context) : this(File(context.filesDir, "lists.json"))

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _deleted = MutableStateFlow(loadDeleted())
    val deletedIds: StateFlow<List<String>> = _deleted.asStateFlow()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<LocalItem>> = _items.asStateFlow()

    fun add(kind: String, text: String): String {
        val id = System.currentTimeMillis().toString(36)
        val next = listOf(
            LocalItem(
                id = id,
                kind = kind,
                text = text,
            ),
        ) + _items.value
        persist(next)
        return id
    }

    fun remove(id: String) {
        val deleted = (_deleted.value + id).distinct()
        persistDeleted(deleted)
        persist(_items.value.filterNot { it.id == id })
    }

    fun update(id: String, text: String, now: Long = System.currentTimeMillis()) {
        persist(
            _items.value.map { item ->
                if (item.id != id) item else item.copy(text = text, updatedAt = now)
            },
        )
    }

    fun moveOpen(from: Int, to: Int) {
        persist(HomeTodos.moveOpen(_items.value, from, to))
    }

    fun toggleComplete(id: String, now: Long = System.currentTimeMillis()) {
        // A toggle is an edit: bump updatedAt so sync last-write-wins (BackupMerge.stamp)
        // still sees the change when completedAt is cleared back to null.
        persist(
            _items.value.map { item ->
                if (item.id != id) item
                else if (item.completedAt != null) item.copy(completedAt = null, updatedAt = now)
                else item.copy(completedAt = now, updatedAt = now)
            },
        )
    }

    fun replaceAll(next: List<LocalItem>, deleted: List<String>) {
        val drop = deleted.distinct()
        persistDeleted(drop)
        persist(next.filterNot { it.id in drop.toSet() })
    }

    private fun persist(next: List<LocalItem>) {
        _items.value = next
        file.writeText(json.encodeToString(next))
    }

    private fun persistDeleted(next: List<String>) {
        _deleted.value = next
        deletedFile.writeText(json.encodeToString(next))
    }

    private fun loadDeleted(): List<String> {
        if (!deletedFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<String>>(deletedFile.readText())
        }.getOrDefault(emptyList())
    }

    private fun load(): List<LocalItem> {
        if (!file.exists()) return emptyList()
        val raw = runCatching {
            json.decodeFromString<List<LocalItem>>(file.readText())
        }.getOrDefault(emptyList())
        val migrated = HomeTodos.migrateOpenOrder(raw)
        val drop = _deleted.value.toSet()
        val next = migrated.filterNot { it.id in drop }
        if (next != raw) {
            runCatching { file.writeText(json.encodeToString(next)) }
        }
        return next
    }
}

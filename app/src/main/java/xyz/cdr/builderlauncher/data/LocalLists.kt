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
) {
    val done: Boolean get() = completedAt != null
    val editedAt: Long get() = if (updatedAt > 0L) updatedAt else createdAt
}

class LocalLists(context: Context) {
    private val file = File(context.filesDir, "lists.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<LocalItem>> = _items.asStateFlow()

    fun add(kind: String, text: String) {
        val next = listOf(
            LocalItem(
                id = System.currentTimeMillis().toString(36),
                kind = kind,
                text = text,
            ),
        ) + _items.value
        persist(next)
    }

    fun remove(id: String) {
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
        persist(
            _items.value.map { item ->
                if (item.id != id) item
                else if (item.completedAt != null) item.copy(completedAt = null)
                else item.copy(completedAt = now)
            },
        )
    }

    fun replaceAll(next: List<LocalItem>) {
        persist(next)
    }

    private fun persist(next: List<LocalItem>) {
        _items.value = next
        file.writeText(json.encodeToString(next))
    }

    private fun load(): List<LocalItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<LocalItem>>(file.readText())
        }.getOrDefault(emptyList())
    }
}

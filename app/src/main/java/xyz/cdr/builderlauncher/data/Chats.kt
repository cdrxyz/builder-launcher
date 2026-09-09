package xyz.cdr.builderlauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val fromUser: Boolean get() = role.equals("user", ignoreCase = true)
    val isNotice: Boolean get() = role.equals("notice", ignoreCase = true)
}

@Serializable
data class ChatThread(
    val id: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val messages: List<ChatMessage> = emptyList(),
)

object Chats {
    const val BACK = "<"
    const val PREFIX = "?"

    fun questionFromInput(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith(PREFIX)) trimmed.drop(1).trim() else trimmed
    }

    fun title(messages: List<ChatMessage>): String {
        val first = messages.firstOrNull { it.fromUser }
            ?.content
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
        val compact = first.replace(Regex("\\s+"), " ")
        return compact.ifBlank { "untitled" }.take(48)
    }

    fun of(threads: List<ChatThread>): List<ChatThread> =
        threads.filter { it.messages.isNotEmpty() }.sortedByDescending { it.updatedAt }

    fun editedLabel(
        millis: Long,
        locale: Locale = Locale.getDefault(),
        zone: TimeZone = TimeZone.getDefault(),
    ): String {
        val fmt = SimpleDateFormat("d MMM HH:mm", locale)
        fmt.timeZone = zone
        return fmt.format(Date(millis))
    }
}

class ChatStore(context: Context) {
    private val file = File(context.filesDir, "chats.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _threads = MutableStateFlow(load())
    val threads: StateFlow<List<ChatThread>> = _threads.asStateFlow()

    fun get(id: String): ChatThread? = _threads.value.find { it.id == id }

    fun addMessage(
        id: String?,
        message: ChatMessage,
        now: Long = System.currentTimeMillis(),
    ): ChatThread {
        if (id == null) {
            val created = ChatThread(
                id = now.toString(36),
                createdAt = now,
                updatedAt = now,
                messages = listOf(message),
            )
            persist(listOf(created) + _threads.value)
            return created
        }
        val existing = get(id)
        if (existing == null) return addMessage(null, message, now)
        val next = existing.copy(
            messages = existing.messages + message,
            updatedAt = now,
        )
        persist(_threads.value.map { if (it.id == id) next else it })
        return next
    }

    fun remove(id: String) {
        persist(_threads.value.filterNot { it.id == id })
    }

    fun replaceAll(next: List<ChatThread>) {
        persist(next)
    }

    private fun persist(next: List<ChatThread>) {
        _threads.value = next
        file.writeText(json.encodeToString(next))
    }

    private fun load(): List<ChatThread> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ChatThread>>(file.readText())
        }.getOrDefault(emptyList())
    }
}

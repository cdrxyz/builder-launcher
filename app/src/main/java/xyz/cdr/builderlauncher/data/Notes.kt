package xyz.cdr.builderlauncher.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Notes {
    const val MORE = "… all notes >"
    const val BACK = "<"
    const val PREFIX = "+"
    const val COMMAND = "notes"

    fun of(items: List<LocalItem>): List<LocalItem> =
        items.filter { it.kind.equals("note", ignoreCase = true) }
            .sortedByDescending { it.editedAt }

    fun title(text: String): String {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val stripped = first.replace(Regex("^#+\\s*"), "").trim()
        return stripped.ifBlank { "untitled" }
    }

    fun matchesQuery(query: String): Boolean {
        val q = query.trim().lowercase()
        return q.isNotEmpty() && COMMAND.startsWith(q)
    }

    fun draftFromInput(value: String): String {
        if (!value.startsWith(PREFIX)) return value
        return value.drop(1).trimStart()
    }

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

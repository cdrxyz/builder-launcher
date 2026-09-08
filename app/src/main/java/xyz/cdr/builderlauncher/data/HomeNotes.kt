package xyz.cdr.builderlauncher.data

object HomeNotes {
    const val NOTE_PREFIX = "+"
    const val BACK = "<"

    fun of(items: List<LocalItem>): List<LocalItem> =
        items.filter { it.kind.equals("note", ignoreCase = true) }

    fun enterDraft(): String = NOTE_PREFIX

    fun keepDraft(input: String): String = input.ifBlank { NOTE_PREFIX }

    fun leaveDraft(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.isEmpty() || trimmed == NOTE_PREFIX) "" else input
    }

    fun title(text: String): String =
        text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().ifBlank { "note" }
}

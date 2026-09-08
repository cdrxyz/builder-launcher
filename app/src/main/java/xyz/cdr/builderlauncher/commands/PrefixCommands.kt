package xyz.cdr.builderlauncher.commands

data class PrefixCommand(val glyph: Char, val label: String)

object PrefixCommands {
    const val DEFAULT_PROMPT = '>'

    val all: List<PrefixCommand> = listOf(
        PrefixCommand('@', "text"),
        PrefixCommand('#', "call"),
        PrefixCommand('*', "calendar"),
        PrefixCommand('-', "todo"),
        PrefixCommand('+', "note"),
        PrefixCommand('?', "ask AI"),
    )

    data class Mode(
        val prompt: Char = DEFAULT_PROMPT,
        val input: String = "",
    ) {
        val line: String
            get() = if (find(prompt) != null) "$prompt$input" else input

        val hasTypedText: Boolean
            get() = input.isNotBlank()

        val cancelsDraft: Boolean
            get() = hasTypedText && !input.equals("send", ignoreCase = true)
    }

    fun find(glyph: Char): PrefixCommand? = all.find { it.glyph == glyph }

    fun pick(current: Mode, glyph: Char): Mode = current.copy(prompt = glyph)

    fun type(current: Mode, newInput: String): Mode {
        val first = newInput.firstOrNull()
        return if (first != null && find(first) != null) {
            Mode(prompt = first, input = newInput.drop(1))
        } else {
            current.copy(input = newInput)
        }
    }

    fun clearMode(current: Mode): Mode =
        if (find(current.prompt) != null) Mode(input = current.input) else current
}

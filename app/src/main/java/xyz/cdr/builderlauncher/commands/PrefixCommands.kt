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
        PrefixCommand('$', "stock"),
        PrefixCommand('?', "ask AI"),
        PrefixCommand('/', "slash"),
    )

    data class Mode(
        val prompt: Char = DEFAULT_PROMPT,
        val input: String = "",
    ) {
        val line: String
            get() = if (isModePrompt(prompt)) "$prompt$input" else input

        val hasTypedText: Boolean
            get() = input.isNotBlank()

        val cancelsDraft: Boolean
            get() = hasTypedText && !input.equals("send", ignoreCase = true)
    }

    fun find(glyph: Char): PrefixCommand? = all.find { it.glyph == glyph }

    fun isModePrompt(glyph: Char): Boolean = find(glyph) != null

    fun pick(current: Mode, glyph: Char): Mode = current.copy(prompt = glyph)

    fun usesRawSymbolKeyboard(glyph: Char): Boolean = glyph == '$'

    /** Prose modes. Default stays off so calculator expressions are not rewritten. */
    fun usesAutocorrect(glyph: Char): Boolean = glyph == '-' || glyph == '+' || glyph == '?'

    fun type(current: Mode, newInput: String): Mode {
        val first = newInput.firstOrNull()
        val next = if (first != null && isModePrompt(first)) {
            Mode(prompt = first, input = newInput.drop(1))
        } else {
            current.copy(input = newInput)
        }
        return if (usesRawSymbolKeyboard(next.prompt)) {
            next.copy(input = collapsePeriodSpaces(next.input))
        } else {
            next
        }
    }

    internal fun collapsePeriodSpaces(input: String): String =
        PERIOD_THEN_SPACES.replace(input, ".")

    fun clearMode(current: Mode): Mode =
        if (isModePrompt(current.prompt)) Mode(input = current.input) else current

    private val PERIOD_THEN_SPACES = Regex("""\. +""")
}

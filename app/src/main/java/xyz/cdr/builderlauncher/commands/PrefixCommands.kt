package xyz.cdr.builderlauncher.commands

data class PrefixCommand(val glyph: Char, val label: String)

object PrefixCommands {
    val all: List<PrefixCommand> = listOf(
        PrefixCommand('@', "text"),
        PrefixCommand('#', "call"),
        PrefixCommand('*', "calendar"),
        PrefixCommand('-', "todo"),
        PrefixCommand('+', "note"),
        PrefixCommand('?', "ask AI"),
    )

    fun fill(glyph: Char): String = "$glyph "

    fun find(glyph: Char): PrefixCommand? = all.find { it.glyph == glyph }

    /**
     * Typing while the command menu is open must close it and keep the typed
     * value. Swallowing the first keystroke is what made help feel stuck.
     */
    fun typeWhileOpen(newValue: String): TypedMenuInput =
        TypedMenuInput(menuOpen = false, value = newValue)
}

data class TypedMenuInput(val menuOpen: Boolean, val value: String)

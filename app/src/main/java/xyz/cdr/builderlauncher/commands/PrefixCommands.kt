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
}

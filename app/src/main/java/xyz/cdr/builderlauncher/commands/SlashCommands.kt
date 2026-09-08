package xyz.cdr.builderlauncher.commands

data class SlashCommand(val name: String, val label: String, val command: Command)

object SlashCommands {
    const val PROMPT = '/'

    val all: List<SlashCommand> = listOf(
        SlashCommand("apps", "all apps", Command.OpenApps),
        SlashCommand("help", "commands", Command.Help),
        SlashCommand("hub", "hub", Command.OpenHub),
        SlashCommand("notes", "all notes", Command.OpenNotes),
        SlashCommand("settings", "settings", Command.OpenSettings),
        SlashCommand("stocks", "watchlist", Command.OpenStocks),
    ).sortedBy { it.name }

    fun exact(query: String): SlashCommand? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        return all.find { it.name == q }
    }

    fun matches(query: String): List<SlashCommand> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { it.name.startsWith(q) }
    }

    fun resolve(query: String): SlashCommand? {
        exact(query)?.let { return it }
        return matches(query).singleOrNull()
    }
}

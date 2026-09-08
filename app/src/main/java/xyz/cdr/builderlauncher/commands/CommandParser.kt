package xyz.cdr.builderlauncher.commands

sealed class Command {
    data object Empty : Command()
    data object Help : Command()
    data object OpenSettings : Command()
    data object OpenHub : Command()
    data object OpenNotes : Command()
    data object OpenApps : Command()
    data object OpenStocks : Command()
    data object OpenClock : Command()
    data object OpenWeather : Command()
    data class Stock(val query: String) : Command()
    data class Message(val target: String, val body: String) : Command()
    data class Call(val target: String) : Command()
    data class Event(val title: String, val whenText: String) : Command()
    data class Todo(val text: String) : Command()
    data class Note(val text: String) : Command()
    data class Ask(val question: String) : Command()
    data class LaunchApp(val query: String) : Command()
    data class Pin(val query: String) : Command()
    data class Unpin(val query: String) : Command()
}

object CommandParser {
    fun parse(raw: String): Command {
        val line = raw.trim()
        if (line.isEmpty()) return Command.Empty

        if (line.startsWith("/")) {
            val rest = line.drop(1).trim()
            if (rest.isEmpty()) return Command.Empty
            SlashCommands.resolve(rest)?.let { return it.command }
            return parseNamed(rest) ?: Command.Empty
        }

        if (line == "?") return Command.Help
        parseNamed(line)?.let { return it }

        return when (line.first()) {
            '@' -> {
                val rest = line.drop(1).trim()
                val parts = rest.split(Regex("\\s+"), limit = 2)
                val target = parts[0]
                val body = parts.getOrElse(1) { "" }.trim()
                if (target.isEmpty()) Command.Help else Command.Message(target, body)
            }
            '#' -> {
                val target = line.drop(1).trim()
                if (target.isEmpty()) Command.Help else Command.Call(target)
            }
            '*' -> {
                val rest = line.drop(1).trim()
                if (rest.isEmpty()) Command.Help else splitEvent(rest)
            }
            '-' -> {
                val text = line.drop(1).trim()
                if (text.isEmpty()) Command.Help else Command.Todo(text)
            }
            '+' -> {
                val text = line.drop(1).trim()
                if (text.isEmpty()) Command.Help else Command.Note(text)
            }
            '?' -> {
                val question = line.drop(1).trim()
                if (question.isEmpty()) Command.Help else Command.Ask(question)
            }
            '$' -> {
                val text = line.drop(1).trim()
                if (text.isEmpty()) Command.OpenStocks else Command.Stock(text)
            }
            else -> Command.LaunchApp(line)
        }
    }

    private fun parseNamed(raw: String): Command? {
        val trimmed = raw.trim()
        val lower = trimmed.lowercase()
        SlashCommands.exact(lower)?.let { return it.command }
        when (lower) {
            "stock" -> return Command.OpenStocks
            "pin", "unpin" -> return Command.Help
        }
        if (lower.startsWith("pin ")) {
            val query = trimmed.drop(4).trim()
            return if (query.isEmpty()) Command.Help else Command.Pin(query)
        }
        if (lower.startsWith("unpin ")) {
            val query = trimmed.drop(6).trim()
            return if (query.isEmpty()) Command.Help else Command.Unpin(query)
        }
        return null
    }

    private fun splitEvent(rest: String): Command.Event {
        val time = Regex(
            """(?i)\b((?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+\d{1,2}(?:\s+\d{1,2}(?::\d{2})?\s*(?:a|am|p|pm)?)?|\d{1,2}:\d{2}|\d{1,2}\s*(?:a|am|p|pm)|tomorrow|today|tonight)\b.*$""",
        ).find(rest)
        return if (time != null && time.range.first > 0) {
            Command.Event(
                title = rest.substring(0, time.range.first).trim(),
                whenText = rest.substring(time.range.first).trim(),
            )
        } else {
            Command.Event(title = rest, whenText = "")
        }
    }
}

package xyz.cdr.minimos.commands

sealed class Command {
    data object Empty : Command()
    data object Help : Command()
    data object OpenSettings : Command()
    data object OpenHub : Command()
    data class Message(val target: String, val body: String) : Command()
    data class Call(val target: String) : Command()
    data class Event(val title: String, val whenText: String) : Command()
    data class Todo(val text: String) : Command()
    data class Note(val text: String) : Command()
    data class Ask(val question: String) : Command()
    data class LaunchApp(val query: String) : Command()
}

object CommandParser {
    fun parse(raw: String): Command {
        val line = raw.trim()
        if (line.isEmpty()) return Command.Empty

        val lower = line.lowercase()
        when (lower) {
            "help", "/help", "?" -> return Command.Help
            "settings", "/settings" -> return Command.OpenSettings
            "hub", "/hub" -> return Command.OpenHub
        }

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
            else -> Command.LaunchApp(line)
        }
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

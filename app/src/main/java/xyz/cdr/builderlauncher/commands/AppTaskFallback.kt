package xyz.cdr.builderlauncher.commands

import xyz.cdr.builderlauncher.apps.AppList
import xyz.cdr.builderlauncher.data.Notes
import xyz.cdr.builderlauncher.podcasts.Podcasts
import xyz.cdr.builderlauncher.stocks.Stocks

/** When default-mode typing is not an app, pick calculator, stocks, or a todo. */
object AppTaskFallback {
    const val EXTRA_CHARS = 3

    data class State(val lastMatchLength: Int = 0)

    data class Result(
        val mode: PrefixCommands.Mode,
        val state: State,
        val switched: Boolean,
        val suppressApps: Boolean = false,
    )

    fun reserved(query: String): Boolean {
        if (query.isBlank()) return true
        if (Calculator.looksLike(query)) return true
        if (query.last().isWhitespace()) return false
        if (SlashCommands.matches(query).isNotEmpty()) return true
        if (Notes.matchesQuery(query)) return true
        if (Stocks.matchesQuery(query)) return true
        if (Podcasts.matchesQuery(query)) return true
        if (AppList.matchesCommand(query)) return true
        return query.equals("timer", ignoreCase = true)
    }

    fun step(mode: PrefixCommands.Mode, matchCount: Int, state: State): Result {
        if (mode.prompt != PrefixCommands.DEFAULT_PROMPT) {
            return Result(mode, State(), false)
        }
        if (AppPickQuery.parse(mode.line).pick != AppPick.Launch) {
            return Result(mode, State(), false)
        }
        val query = mode.input
        if (query.isBlank()) {
            return Result(mode, State(), false)
        }
        if (matchCount == 0 && Stocks.looksLikeTicker(query)) {
            return Result(PrefixCommands.pick(mode, '$'), State(), true)
        }
        if (Calculator.looksLike(query)) {
            return Result(mode, State(lastMatchLength = query.length), false, suppressApps = true)
        }
        if (reserved(query) || matchCount > 0) {
            return Result(mode, State(lastMatchLength = query.length), false)
        }
        val unmatchedFrom = if (query.length < state.lastMatchLength) 0 else state.lastMatchLength
        if (query.length >= unmatchedFrom + EXTRA_CHARS) {
            return Result(PrefixCommands.pick(mode, '-'), State(), true)
        }
        return Result(mode, State(lastMatchLength = unmatchedFrom), false)
    }
}

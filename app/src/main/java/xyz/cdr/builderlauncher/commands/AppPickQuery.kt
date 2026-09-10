package xyz.cdr.builderlauncher.commands

import xyz.cdr.builderlauncher.apps.AppSearch
import xyz.cdr.builderlauncher.apps.LaunchableApp

data class AppPickQuery(val pick: AppPick, val query: String) {
    fun filter(apps: List<LaunchableApp>, pinned: Set<String> = emptySet()): List<LaunchableApp> {
        val hits = AppSearch.filter(apps, query)
        return if (pick == AppPick.Unpin) hits.filter { it.packageName in pinned } else hits
    }

    companion object {
        private val PREFIX = Regex("^(unpin|pin)(?:\\s+|$)(.*)$", RegexOption.IGNORE_CASE)

        fun parse(raw: String): AppPickQuery {
            val trimmed = raw.trim()
            val match = PREFIX.matchEntire(trimmed) ?: return AppPickQuery(AppPick.Launch, trimmed)
            val pick = if (match.groupValues[1].equals("unpin", ignoreCase = true)) {
                AppPick.Unpin
            } else {
                AppPick.Pin
            }
            return AppPickQuery(pick, match.groupValues[2].trim())
        }
    }
}

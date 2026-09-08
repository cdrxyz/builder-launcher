package xyz.cdr.builderlauncher.apps

object AppList {
    const val MORE = "… all apps >"
    const val BACK = "<"
    const val COMMAND = "apps"
    const val PREVIEW = 5

    fun preview(apps: List<LaunchableApp>): List<LaunchableApp> = apps.take(PREVIEW)

    fun matchesCommand(query: String): Boolean {
        val q = query.trim().lowercase()
        return q == COMMAND || q == "/$COMMAND"
    }
}

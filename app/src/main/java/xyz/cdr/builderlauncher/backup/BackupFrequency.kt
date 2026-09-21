package xyz.cdr.builderlauncher.backup

enum class BackupFrequency {
    AUTO,
    OFF,
    DAILY,
    WEEKLY,
    ;

    val label: String get() = name.lowercase()

    val hint: String
        get() = when (this) {
            AUTO ->
                "Default. Uploads a few seconds after a local change. Pulls from the account or S3 every 5 minutes while the launcher is open, and when you open it."
            OFF -> "Manual only. Use backup now or sync now."
            DAILY -> "Uploads when you open the launcher if a day has passed."
            WEEKLY -> "Uploads when you open the launcher if a week has passed."
        }

    fun due(nowMs: Long, lastBackupAtEpochMs: Long): Boolean {
        if (this == OFF || this == AUTO) return false
        if (lastBackupAtEpochMs <= 0L) return true
        val elapsed = nowMs - lastBackupAtEpochMs
        return elapsed >= intervalMs
    }

    private val intervalMs: Long
        get() = when (this) {
            AUTO, OFF -> Long.MAX_VALUE
            DAILY -> 24L * 60 * 60 * 1000
            WEEKLY -> 7L * 24 * 60 * 60 * 1000
        }

    companion object {
        const val WRITE_DEBOUNCE_MS = 2_000L
        const val PULL_INTERVAL_MS = 5L * 60 * 1000

        fun parse(raw: String?): BackupFrequency =
            entries.find { it.name.equals(raw, ignoreCase = true) } ?: AUTO
    }
}

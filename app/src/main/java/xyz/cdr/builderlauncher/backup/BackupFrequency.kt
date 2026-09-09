package xyz.cdr.builderlauncher.backup

enum class BackupFrequency {
    OFF,
    DAILY,
    WEEKLY,
    ;

    val label: String get() = name.lowercase()

    fun due(nowMs: Long, lastBackupAtEpochMs: Long): Boolean {
        if (this == OFF) return false
        if (lastBackupAtEpochMs <= 0L) return true
        val elapsed = nowMs - lastBackupAtEpochMs
        return elapsed >= intervalMs
    }

    private val intervalMs: Long
        get() = when (this) {
            OFF -> Long.MAX_VALUE
            DAILY -> 24L * 60 * 60 * 1000
            WEEKLY -> 7L * 24 * 60 * 60 * 1000
        }

    companion object {
        fun parse(raw: String?): BackupFrequency =
            entries.find { it.name.equals(raw, ignoreCase = true) } ?: OFF
    }
}

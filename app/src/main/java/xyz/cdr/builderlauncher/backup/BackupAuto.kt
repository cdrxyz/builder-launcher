package xyz.cdr.builderlauncher.backup

object BackupAuto {
    enum class Action { NONE, PUSH, PULL }

    fun ready(accountToken: String, s3Ready: Boolean): Boolean =
        accountToken.isNotBlank() || s3Ready

    fun action(
        frequency: BackupFrequency,
        dirty: Boolean,
        lastBackupAtEpochMs: Long,
        lastPullAtEpochMs: Long,
        nowMs: Long,
        ready: Boolean,
    ): Action {
        if (frequency != BackupFrequency.AUTO || !ready) return Action.NONE
        if (dirty) return Action.PUSH
        val pullDue = lastPullAtEpochMs <= 0L ||
            nowMs - lastPullAtEpochMs >= BackupFrequency.PULL_INTERVAL_MS
        if (pullDue) return Action.PULL
        if (lastBackupAtEpochMs <= 0L) return Action.PUSH
        return Action.NONE
    }
}

package xyz.cdr.builderlauncher.backup

object BackupAuto {
    enum class Action { NONE, PUSH, PULL }

    /** What a pull of the account copy has to do with the merged result. */
    enum class Outcome { MATCHED, PUSH_LOCAL, APPLY }

    fun ready(accountToken: String, s3Ready: Boolean): Boolean =
        accountToken.isNotBlank() || s3Ready

    /**
     * MATCHED: nothing differs. PUSH_LOCAL: the merge equals the local document, so
     * the cloud is behind local changes that never rode a push — upload the merge.
     * APPLY: the remote contributed something — apply the merge and push it back.
     */
    fun pullOutcome(localKey: String, remoteKey: String, mergedKey: String): Outcome = when {
        mergedKey != localKey -> Outcome.APPLY
        mergedKey != remoteKey -> Outcome.PUSH_LOCAL
        else -> Outcome.MATCHED
    }

    fun action(
        frequency: BackupFrequency,
        dirty: Boolean,
        lastBackupAtEpochMs: Long,
        lastPullAtEpochMs: Long,
        nowMs: Long,
        ready: Boolean,
    ): Action {
        if (frequency != BackupFrequency.AUTO || !ready) return Action.NONE
        if (dirty && lastPullAtEpochMs > 0L) return Action.PUSH
        val pullDue = lastPullAtEpochMs <= 0L ||
            nowMs - lastPullAtEpochMs >= BackupFrequency.PULL_INTERVAL_MS
        if (pullDue) return Action.PULL
        if (lastBackupAtEpochMs <= 0L) return Action.PUSH
        return Action.NONE
    }
}

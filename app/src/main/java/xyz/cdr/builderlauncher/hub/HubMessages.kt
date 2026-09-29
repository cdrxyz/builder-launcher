package xyz.cdr.builderlauncher.hub

/**
 * Hub is the inbox for conversations a person can answer, not a dump of every
 * notification. Reply uses the notification RemoteInput when the app exposes one;
 * otherwise it opens the same pending intent as tapping the row.
 */
object HubMessages {
    const val BACK = "<"
    const val TITLE = "hub"
    const val CLEAR_ALL = "clear all"

    val knownPackages = setOf(
        "com.google.android.apps.messaging",
        "com.google.android.apps.dynamite",
        "com.android.mms",
        "com.android.messaging",
        "com.samsung.android.messaging",
        "com.sonyericsson.conversations",
        "org.thoughtcrime.securesms",
        "im.molly.app",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.facebook.orca",
        "com.facebook.mlite",
        "com.slack",
        "com.Slack",
        "com.discord",
        "im.vector.app",
        "ch.threema.app",
        "xyz.klinker.messenger",
    )

    private val knownLower = knownPackages.map { it.lowercase() }.toSet()

    fun isReplyable(
        packageName: String,
        ongoing: Boolean = false,
        groupSummary: Boolean = false,
        selection: HubAppSelection = HubAppSelection(),
    ): Boolean {
        if (ongoing || groupSummary) return false
        return selection.checked(packageName)
    }

    fun isKnownMessenger(packageName: String): Boolean {
        val p = packageName.lowercase()
        if (p in knownLower) return true
        return p.contains("signal") ||
            p.contains("molly") ||
            p.contains("securesms") ||
            p.contains("slack")
    }

    /** Slack often leaves android.text empty and puts the preview in the message lines. */
    fun notificationBody(
        text: String,
        bigText: String = "",
        lines: List<String> = emptyList(),
        summary: String = "",
    ): String {
        if (text.isNotBlank()) return text.trim()
        if (bigText.isNotBlank()) return bigText.trim()
        val joined = lines.map { it.trim() }.filter { it.isNotEmpty() }.takeLast(3).joinToString("\n")
        if (joined.isNotBlank()) return joined
        return summary.trim()
    }
}

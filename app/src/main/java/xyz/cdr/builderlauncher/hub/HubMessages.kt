package xyz.cdr.builderlauncher.hub

/**
 * Hub is the inbox for conversations a person can answer, not a dump of every
 * notification. Reply uses the notification RemoteInput when the app exposes one;
 * otherwise it opens the same pending intent as tapping the row.
 */
object HubMessages {
    const val BACK = "<"
    const val CATEGORY_MESSAGE = "msg"
    const val MESSAGING_STYLE = "android.app.Notification\$MessagingStyle"

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
        "com.discord",
        "im.vector.app",
        "ch.threema.app",
        "xyz.klinker.messenger",
    )

    fun isReplyable(
        packageName: String,
        category: String?,
        template: String?,
        ongoing: Boolean = false,
        groupSummary: Boolean = false,
    ): Boolean {
        if (ongoing || groupSummary) return false
        if (category == CATEGORY_MESSAGE) return true
        if (template == MESSAGING_STYLE) return true
        return isKnownMessenger(packageName)
    }

    fun isKnownMessenger(packageName: String): Boolean {
        val p = packageName.lowercase()
        if (p in knownPackages) return true
        return p.contains("signal") || p.contains("molly") || p.contains("securesms")
    }
}

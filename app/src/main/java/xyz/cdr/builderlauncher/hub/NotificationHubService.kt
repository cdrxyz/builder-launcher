package xyz.cdr.builderlauncher.hub

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import xyz.cdr.builderlauncher.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HubItem(
    val key: String,
    val source: String,
    val title: String,
    val body: String,
    val postedAt: Long,
    val packageName: String,
    val notifId: Int,
    val tag: String?,
    val canInlineReply: Boolean,
)

object HubStore {
    private val _items = MutableStateFlow<List<HubItem>>(emptyList())
    val items: StateFlow<List<HubItem>> = _items.asStateFlow()

    @Volatile
    var open: (String) -> Boolean = { false }

    @Volatile
    var dismiss: (String) -> Unit = {}

    @Volatile
    var reply: (String, String) -> Boolean = { _, _ -> false }

    fun upsert(item: HubItem) {
        val without = _items.value.filterNot { it.key == item.key }
        _items.value = (listOf(item) + without).take(80)
    }

    fun remove(key: String) {
        _items.value = _items.value.filterNot { it.key == key }
    }

    fun replace(items: List<HubItem>) {
        _items.value = items.take(80)
    }

    fun clearAll() {
        val keys = _items.value.map { it.key }
        keys.forEach { key -> dismiss(key) }
        if (_items.value.isNotEmpty()) replace(emptyList())
    }
}

class NotificationHubService : NotificationListenerService() {
    private val intents = mutableMapOf<String, PendingIntent?>()
    private val replies = mutableMapOf<String, Pair<PendingIntent, Array<RemoteInput>>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var filterJob: Job? = null

    override fun onListenerConnected() {
        HubStore.open = { key -> open(key) }
        HubStore.dismiss = { key -> dismiss(key) }
        HubStore.reply = { key, text -> reply(key, text) }
        val disk = runCatching { SettingsRepository(this).settings.value }.getOrNull()
        if (disk != null) {
            HubFilter.ensure(HubAppSelection(disk.hubRestrict, disk.hubPackages))
        }
        filterJob?.cancel()
        filterJob = scope.launch {
            HubFilter.selection.collect { refreshActive() }
        }
    }

    override fun onListenerDisconnected() {
        filterJob?.cancel()
        HubStore.open = { false }
        HubStore.dismiss = {}
        HubStore.reply = { _, _ -> false }
        intents.clear()
        replies.clear()
    }

    override fun onDestroy() {
        filterJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val item = toItem(sbn)
        if (item == null) {
            val key = "${sbn.packageName}:${sbn.id}:${sbn.tag}"
            intents.remove(key)
            replies.remove(key)
            HubStore.remove(key)
            return
        }
        HubStore.upsert(item)
        pruneIntents()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val key = "${sbn.packageName}:${sbn.id}:${sbn.tag}"
        intents.remove(key)
        replies.remove(key)
        HubStore.remove(key)
    }

    private fun open(key: String): Boolean {
        val pending = intents[key]
        if (pending != null && sendPending(pending)) return true
        val pkg = HubStore.items.value.find { it.key == key }?.packageName ?: return false
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(launch)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sendPending(pending: PendingIntent): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                }
                pending.send(this, 0, null, null, null, null, options.toBundle())
            } else {
                pending.send()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun reply(key: String, text: String): Boolean {
        val stored = replies[key]
        if (stored == null || text.isBlank()) return open(key)
        val (pending, inputs) = stored
        val intent = Intent()
        val results = Bundle()
        for (input in inputs) {
            results.putCharSequence(input.resultKey, text)
        }
        RemoteInput.addResultsToIntent(inputs, intent, results)
        return try {
            pending.send(this, 0, intent)
            true
        } catch (_: Exception) {
            open(key)
        }
    }

    private fun dismiss(key: String) {
        val item = HubStore.items.value.find { it.key == key } ?: return
        runCatching { cancelNotification(item.key) }
        intents.remove(key)
        replies.remove(key)
        HubStore.remove(key)
    }

    private fun refreshActive() {
        val posted = runCatching { activeNotifications }.getOrNull() ?: return
        val items = posted.mapNotNull { toItem(it) }
        HubStore.replace(items)
        pruneIntents()
    }

    private fun pruneIntents() {
        val keep = HubStore.items.value.map { it.key }.toSet()
        intents.keys.retainAll(keep)
        replies.keys.retainAll(keep)
    }

    private fun toItem(sbn: StatusBarNotification): HubItem? {
        if (sbn.packageName == packageName) return null
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val summary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()
        val conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty()
        val lines = messageLines(extras)
        val body = HubMessages.notificationBody(text, big, lines, summary)
        val shownTitle = title.ifBlank { conversation }
        if (shownTitle.isBlank() && body.isBlank()) return null
        val replyAction = notification.actions?.firstOrNull { action ->
            action.remoteInputs?.any { it.resultKey.isNotBlank() } == true
        }
        val remoteInputs = replyAction?.remoteInputs
        val hasRemoteInput = replyAction != null && remoteInputs != null
        val ongoing = sbn.isOngoing ||
            (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        val groupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (!HubMessages.isReplyable(
                packageName = sbn.packageName,
                ongoing = ongoing,
                groupSummary = groupSummary,
                selection = HubFilter.selection.value,
            )
        ) {
            return null
        }
        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0),
            ).toString()
        } catch (_: Exception) {
            sbn.packageName
        }
        val key = "${sbn.packageName}:${sbn.id}:${sbn.tag}"
        intents[key] = notification.contentIntent
        if (replyAction != null && remoteInputs != null) {
            replies[key] = replyAction.actionIntent to remoteInputs
        } else {
            replies.remove(key)
        }
        return HubItem(
            key = key,
            source = label,
            title = shownTitle.ifBlank { label },
            body = body,
            postedAt = sbn.postTime,
            packageName = sbn.packageName,
            notifId = sbn.id,
            tag = sbn.tag,
            canInlineReply = hasRemoteInput,
        )
    }

    private fun messageLines(extras: Bundle): List<String> = runCatching {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }
        val fromStyle = raw.orEmpty().mapNotNull { item ->
            val bundle = item as? Bundle ?: return@mapNotNull null
            bundle.getCharSequence("text")?.toString()
        }
        val fromLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString() }
            .orEmpty()
        fromStyle + fromLines
    }.getOrDefault(emptyList())
}

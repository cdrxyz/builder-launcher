package xyz.cdr.builderlauncher.hub

import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
)

object HubStore {
    private val _items = MutableStateFlow<List<HubItem>>(emptyList())
    val items: StateFlow<List<HubItem>> = _items.asStateFlow()

    @Volatile
    var open: (String) -> Boolean = { false }

    @Volatile
    var dismiss: (String) -> Unit = {}

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
}

class NotificationHubService : NotificationListenerService() {
    private val intents = mutableMapOf<String, PendingIntent?>()

    override fun onListenerConnected() {
        HubStore.open = { key -> open(key) }
        HubStore.dismiss = { key -> dismiss(key) }
        val items = activeNotifications.mapNotNull { toItem(it) }
        HubStore.replace(items)
    }

    override fun onListenerDisconnected() {
        HubStore.open = { false }
        HubStore.dismiss = {}
        intents.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val item = toItem(sbn) ?: return
        HubStore.upsert(item)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val key = "${sbn.packageName}:${sbn.id}:${sbn.tag}"
        intents.remove(key)
        HubStore.remove(key)
    }

    private fun open(key: String): Boolean {
        val intent = intents[key] ?: return false
        return try {
            intent.send()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun dismiss(key: String) {
        val item = HubStore.items.value.find { it.key == key } ?: return
        runCatching { cancelNotification(item.packageName, item.tag, item.notifId) }
        intents.remove(key)
        HubStore.remove(key)
    }

    private fun toItem(sbn: StatusBarNotification): HubItem? {
        if (sbn.packageName == packageName) return null
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0),
            ).toString()
        } catch (_: Exception) {
            sbn.packageName
        }
        val key = "${sbn.packageName}:${sbn.id}:${sbn.tag}"
        intents[key] = sbn.notification.contentIntent
        return HubItem(
            key = key,
            source = label,
            title = title.ifBlank { label },
            body = text,
            postedAt = sbn.postTime,
            packageName = sbn.packageName,
            notifId = sbn.id,
            tag = sbn.tag,
        )
    }
}

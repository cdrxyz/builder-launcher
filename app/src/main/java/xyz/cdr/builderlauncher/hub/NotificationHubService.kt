package xyz.cdr.builderlauncher.hub

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
)

object HubStore {
    private val _items = MutableStateFlow<List<HubItem>>(emptyList())
    val items: StateFlow<List<HubItem>> = _items.asStateFlow()

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
    override fun onListenerConnected() {
        val items = activeNotifications.mapNotNull { toItem(it) }
        HubStore.replace(items)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val item = toItem(sbn) ?: return
        HubStore.upsert(item)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        HubStore.remove("${sbn.packageName}:${sbn.id}:${sbn.tag}")
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
        return HubItem(
            key = "${sbn.packageName}:${sbn.id}:${sbn.tag}",
            source = label,
            title = title.ifBlank { label },
            body = text,
            postedAt = sbn.postTime,
        )
    }
}

package xyz.cdr.builderlauncher.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubStoreTest {
    @Test
    fun clearAllEmptiesItemsWhenListenerIsDisconnected() {
        HubStore.replace(listOf(item("1"), item("2")))
        HubStore.dismiss = {}
        HubStore.clearAll()
        assertTrue(HubStore.items.value.isEmpty())
    }

    @Test
    fun clearAllDismissesEachKey() {
        val seen = mutableListOf<String>()
        HubStore.replace(listOf(item("1"), item("2")))
        HubStore.dismiss = { key ->
            seen += key
            HubStore.remove(key)
        }
        HubStore.clearAll()
        assertEquals(listOf("1", "2"), seen)
        assertTrue(HubStore.items.value.isEmpty())
        HubStore.dismiss = {}
    }

    private fun item(key: String) = HubItem(
        key = key,
        source = "Signal",
        title = "Ada",
        body = "hi",
        postedAt = 1L,
        packageName = "org.thoughtcrime.securesms",
        notifId = 1,
        tag = null,
        canInlineReply = true,
    )
}

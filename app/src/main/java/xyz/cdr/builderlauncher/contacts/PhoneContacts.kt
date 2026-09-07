package xyz.cdr.builderlauncher.contacts

import android.content.Context
import android.provider.ContactsContract

class PhoneContacts(private val context: Context) {
    @Volatile
    private var cache: List<PhoneContact> = emptyList()

    @Volatile
    private var loadedAt: Long = 0L

    fun all(): List<PhoneContact> {
        val now = System.currentTimeMillis()
        if (loadedAt != 0L && now - loadedAt < 30_000) return cache
        val next = load()
        cache = next
        loadedAt = now
        return next
    }

    fun invalidate() {
        loadedAt = 0L
    }

    fun search(query: String): List<PhoneContact> = ContactMatch.filter(all(), query)

    private fun load(): List<PhoneContact> {
        val cr = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        )
        return try {
            cr.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx)?.trim().orEmpty()
                        val number = cursor.getString(numIdx)?.trim().orEmpty()
                        if (name.isNotEmpty() && number.isNotEmpty()) {
                            add(PhoneContact(name, number))
                        }
                    }
                }
            } ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}

package xyz.cdr.builderlauncher.contacts

import android.content.Context
import android.provider.ContactsContract

class PhoneContacts(private val context: Context) {
    fun all(): List<PhoneContact> {
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

    fun search(query: String): List<PhoneContact> = ContactMatch.filter(all(), query)

    fun resolve(target: String): PhoneContact? {
        val t = target.trim()
        if (t.isEmpty()) return null
        if (t.any { it.isDigit() } && t.none { it.isLetter() }) {
            return PhoneContact(t, t)
        }
        val matches = search(t)
        return matches.singleOrNull() ?: matches.firstOrNull()
    }
}

package xyz.cdr.builderlauncher.sms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import xyz.cdr.builderlauncher.contacts.PhoneContact

class SmsSender(private val context: Context) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun send(number: String, body: String): Boolean {
        val dest = number.filter { it.isDigit() || it == '+' }
        if (dest.isEmpty() || body.isBlank()) return false
        return try {
            val sms = SmsManager.getDefault()
            val parts = sms.divideMessage(body)
            if (parts.size <= 1) {
                sms.sendTextMessage(dest, null, body, null, null)
            } else {
                sms.sendMultipartTextMessage(dest, null, parts, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun composeFallback(contact: PhoneContact, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.number}"))
            .putExtra("sms_body", body)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

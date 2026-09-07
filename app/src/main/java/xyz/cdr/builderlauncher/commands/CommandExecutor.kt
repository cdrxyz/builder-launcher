package xyz.cdr.builderlauncher.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import xyz.cdr.builderlauncher.apps.InstalledApps
import xyz.cdr.builderlauncher.apps.LaunchableApp
import xyz.cdr.builderlauncher.data.LocalLists

class CommandExecutor(
    private val context: Context,
    private val apps: InstalledApps,
    private val lists: LocalLists,
) {
    fun execute(command: Command): ExecResult {
        return when (command) {
            Command.Empty -> ExecResult.None
            Command.Help -> ExecResult.ShowHelp
            Command.OpenSettings -> ExecResult.NavigateSettings
            Command.OpenHub -> ExecResult.NavigateHub
            is Command.Message -> {
                val dest = resolvePhone(command.target) ?: command.target
                val uri = Uri.parse("smsto:$dest")
                val intent = Intent(Intent.ACTION_SENDTO, uri)
                    .putExtra("sms_body", command.body)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startOrToast(intent, "No messaging app")
                ExecResult.None
            }
            is Command.Call -> {
                val dest = resolvePhone(command.target) ?: command.target
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dest"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startOrToast(intent, "Cannot dial")
                ExecResult.None
            }
            is Command.Event -> {
                val intent = Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, command.title)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
                    .putExtra(Intent.EXTRA_TEXT, command.whenText)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startOrToast(intent, "No calendar app")
                ExecResult.None
            }
            is Command.Todo -> {
                lists.add("todo", command.text)
                toast("Todo saved")
                ExecResult.None
            }
            is Command.Note -> {
                lists.add("note", command.text)
                toast("Note saved")
                ExecResult.None
            }
            is Command.Ask -> ExecResult.Ask(command.question)
            is Command.LaunchApp -> {
                val matches = apps.search(command.query)
                when {
                    matches.size == 1 -> {
                        apps.launch(matches.first())
                        ExecResult.None
                    }
                    matches.isEmpty() && command.query.equals("timer", true) -> {
                        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                            .putExtra(AlarmClock.EXTRA_LENGTH, 300)
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startOrToast(intent, "No clock app")
                        ExecResult.None
                    }
                    else -> ExecResult.AppChoices(command.query, matches)
                }
            }
        }
    }

    private fun resolvePhone(target: String): String? {
        if (target.any { it.isDigit() } && target.none { it.isLetter() }) return target
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
                val needle = target.lowercase()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    if (name.lowercase().contains(needle)) {
                        return cursor.getString(numIdx)
                    }
                }
                null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun startOrToast(intent: Intent, error: String) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            toast(error)
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}

sealed class ExecResult {
    data object None : ExecResult()
    data object ShowHelp : ExecResult()
    data object NavigateSettings : ExecResult()
    data object NavigateHub : ExecResult()
    data class Ask(val question: String) : ExecResult()
    data class AppChoices(val query: String, val apps: List<LaunchableApp>) : ExecResult()
}

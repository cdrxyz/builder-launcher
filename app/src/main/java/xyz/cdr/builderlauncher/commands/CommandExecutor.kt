package xyz.cdr.builderlauncher.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import xyz.cdr.builderlauncher.apps.InstalledApps
import xyz.cdr.builderlauncher.apps.LaunchableApp
import xyz.cdr.builderlauncher.contacts.PhoneContact
import xyz.cdr.builderlauncher.contacts.PhoneContacts
import xyz.cdr.builderlauncher.data.LocalLists
import xyz.cdr.builderlauncher.data.PinnedApps
import xyz.cdr.builderlauncher.sms.SmsSender

class CommandExecutor(
    private val context: Context,
    private val apps: InstalledApps,
    private val lists: LocalLists,
    private val pins: PinnedApps,
    private val contacts: PhoneContacts,
    private val sms: SmsSender,
) {
    fun execute(command: Command): ExecResult {
        return when (command) {
            Command.Empty -> ExecResult.None
            Command.Help -> ExecResult.ShowHelp
            Command.OpenSettings -> ExecResult.NavigateSettings
            Command.OpenHub -> ExecResult.NavigateHub
            Command.OpenNotes -> ExecResult.NavigateNotes
            is Command.Message -> contactAction(command.target, command.body, ContactAction.Message)
            is Command.Call -> contactAction(command.target, "", ContactAction.Call)
            is Command.Event -> {
                val start = EventWhen.millis(command.whenText)
                val intent = Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, command.title)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (start != null) {
                    intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                } else if (command.whenText.isNotBlank()) {
                    intent.putExtra(CalendarContract.Events.DESCRIPTION, command.whenText)
                }
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
            is Command.LaunchApp -> pickApp(command.query, AppPick.Launch)
            is Command.Pin -> pickApp(command.query, AppPick.Pin)
            is Command.Unpin -> pickApp(command.query, AppPick.Unpin)
        }
    }

    private fun pickApp(query: String, pick: AppPick): ExecResult {
        val matches = when (pick) {
            AppPick.Unpin -> {
                val pinned = pins.packages().toSet()
                apps.search(query).filter { it.packageName in pinned }
            }
            else -> apps.search(query)
        }
        return when {
            matches.size == 1 -> {
                applyPick(matches.first(), pick)
                ExecResult.None
            }
            matches.isEmpty() && pick == AppPick.Launch && query.equals("timer", true) -> {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, 300)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startOrToast(intent, "No clock app")
                ExecResult.None
            }
            matches.isEmpty() -> {
                toast("No app matches")
                ExecResult.None
            }
            else -> ExecResult.AppChoices(query, matches, pick)
        }
    }

    fun applyPick(app: LaunchableApp, pick: AppPick) {
        when (pick) {
            AppPick.Launch -> apps.launch(app)
            AppPick.Pin -> {
                pins.pin(app.packageName)
                toast("Pinned ${app.label}")
            }
            AppPick.Unpin -> {
                pins.unpin(app.packageName)
                toast("Unpinned ${app.label}")
            }
        }
    }

    private fun contactAction(target: String, body: String, action: ContactAction): ExecResult {
        val numeric = target.any { it.isDigit() } && target.none { it.isLetter() }
        if (numeric) {
            return applyContact(PhoneContact(target, target), body, action)
        }
        val matches = contacts.search(target)
        return when {
            matches.size == 1 -> applyContact(matches.first(), body, action)
            matches.isEmpty() -> {
                toast("No contact matches")
                ExecResult.None
            }
            else -> ExecResult.ContactChoices(matches, body, action)
        }
    }

    fun applyContact(contact: PhoneContact, body: String, action: ContactAction): ExecResult {
        return when (action) {
            ContactAction.Message -> {
                if (body.isBlank()) {
                    toast("Add a message after the name")
                    ExecResult.None
                } else {
                    ExecResult.SmsDraft(contact, body)
                }
            }
            ContactAction.Call -> {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.number}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startOrToast(intent, "Cannot dial")
                ExecResult.None
            }
        }
    }

    fun sendSms(contact: PhoneContact, body: String): Boolean {
        if (sms.hasPermission() && sms.send(contact.number, body)) {
            toast("Sent to ${contact.name}")
            return true
        }
        return try {
            sms.composeFallback(contact, body)
            toast("Opened Messages to send")
            false
        } catch (_: Exception) {
            toast("Cannot send")
            false
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

enum class AppPick { Launch, Pin, Unpin }

enum class ContactAction { Message, Call }

sealed class ExecResult {
    data object None : ExecResult()
    data object ShowHelp : ExecResult()
    data object NavigateSettings : ExecResult()
    data object NavigateHub : ExecResult()
    data object NavigateNotes : ExecResult()
    data class Ask(val question: String) : ExecResult()
    data class AppChoices(val query: String, val apps: List<LaunchableApp>, val pick: AppPick = AppPick.Launch) : ExecResult()
    data class ContactChoices(val contacts: List<PhoneContact>, val body: String, val action: ContactAction) : ExecResult()
    data class SmsDraft(val contact: PhoneContact, val body: String) : ExecResult()
}

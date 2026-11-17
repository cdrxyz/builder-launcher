package xyz.cdr.builderlauncher.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import xyz.cdr.builderlauncher.clock.ClockSound
import xyz.cdr.builderlauncher.clock.ClockSoundPlayer
import xyz.cdr.builderlauncher.data.SettingsRepository
import xyz.cdr.builderlauncher.MainActivity
import xyz.cdr.builderlauncher.R
import java.time.ZoneId

object HabitScheduler {
    const val ACTION_REMIND = "xyz.cdr.builderlauncher.habit.REMIND"
    const val ACTION_CHECK = "xyz.cdr.builderlauncher.habit.CHECK"
    const val EXTRA_HABIT_ID = "habit_id"
    const val EXTRA_SLOT = "slot_at"
    private const val CHANNEL = "habits"
    private const val REQUEST = 0x4AB17000
    private const val PREFS = "habit-reminders"

    fun reconcile(context: Context, store: HabitStore, now: Long = System.currentTimeMillis()) {
        val settings = SettingsRepository(context).settings.value
        if (!settings.habitReminders) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending(context, ACTION_REMIND, REQUEST))
            return
        }
        showDue(context, store, settings.habitSound, now)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val request = pending(context, ACTION_REMIND, REQUEST)
        val at = Habits.nextReminderAt(store.state.value.habits, store.state.value.marks, now, ZoneId.systemDefault())
        if (at == null) {
            am.cancel(request)
            return
        }
        val whenAt = if (at <= now) now + 1_000L else at
        schedule(am, request, whenAt)
    }

    fun showDue(context: Context, store: HabitStore, sound: ClockSound, now: Long = System.currentTimeMillis()) {
        val zone = ZoneId.systemDefault()
        val reminded = reminded(context)
        val due = Habits.dueReminder(store.state.value.habits, store.state.value.marks, now, reminded, zone) ?: return
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val habit = store.state.value.habits.find { it.id == due.habitId }
        val body = habit?.let { Habits.scheduleLabel(it) } ?: "due"
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val check = Intent(context, HabitReceiver::class.java)
            .setAction(ACTION_CHECK)
            .putExtra(EXTRA_HABIT_ID, due.habitId)
            .putExtra(EXTRA_SLOT, due.slotAt)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(due.name)
            .setContentText(body)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, "check", pendingBroadcast(context, check, due.habitId.hashCode()))
            .build()
        nm.notify(due.habitId.hashCode(), notification)
        remember(context, Habits.markId(due.habitId, due.slotAt))
        if (!sound.silent) ClockSoundPlayer.preview(context, sound)
    }

    private fun schedule(am: AlarmManager, request: PendingIntent, at: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, request)
            return
        }
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, request) }
            .onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, request) }
    }

    private fun pending(context: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            request,
            Intent(context, HabitReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun pendingBroadcast(context: Context, intent: Intent, request: Int): PendingIntent =
        PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Habits", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun reminded(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("shown", emptySet()).orEmpty()

    private fun remember(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("shown", reminded(context) + key).apply()
    }
}

class HabitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = HabitStore(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                HabitScheduler.reconcile(context, store)
            }
            HabitScheduler.ACTION_REMIND -> {
                val settings = SettingsRepository(context).settings.value
                if (settings.habitReminders) HabitScheduler.reconcile(context, store)
            }
            HabitScheduler.ACTION_CHECK -> {
                val id = intent.getStringExtra(HabitScheduler.EXTRA_HABIT_ID) ?: return
                val slot = intent.getLongExtra(HabitScheduler.EXTRA_SLOT, 0L)
                if (slot != 0L) store.complete(id, slot)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(id.hashCode())
                HabitScheduler.reconcile(context, store)
            }
        }
    }
}

package xyz.cdr.builderlauncher.clock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import xyz.cdr.builderlauncher.MainActivity
import xyz.cdr.builderlauncher.R

class ClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ClockStore(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                ClockScheduler.sync(context, store.snapshot())
            }
            ClockScheduler.ACTION_TIMER -> {
                store.setTimer(Clock.reset(store.snapshot().timer))
                notify(context, NOTIFY_TIMER, "Timer", "Time is up.")
            }
            ClockScheduler.ACTION_ALARM -> {
                val id = intent.getStringExtra(ClockScheduler.EXTRA_ALARM_ID)
                val alarm = store.snapshot().alarms.find { it.id == id }
                val title = alarm?.label?.ifBlank { "Alarm" } ?: "Alarm"
                val body = alarm?.let { Clock.formatAlarm(it.hour, it.minute) } ?: "Alarm"
                notify(context, NOTIFY_ALARM, title, body)
                ClockScheduler.sync(context, store.snapshot())
            }
        }
    }

    private fun notify(context: Context, id: Int, title: String, body: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Clock", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(id, n)
    }

    companion object {
        private const val CHANNEL = "clock"
        private const val NOTIFY_TIMER = 7101
        private const val NOTIFY_ALARM = 7102
    }
}

package xyz.cdr.builderlauncher.clock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import xyz.cdr.builderlauncher.MainActivity
import xyz.cdr.builderlauncher.R
import xyz.cdr.builderlauncher.data.SettingsRepository

class ClockAlertService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP, ACTION_DISMISS -> {
                finishAlert()
                return START_NOT_STICKY
            }
            ACTION_RUN_AGAIN -> {
                val store = ClockStore.get(this)
                val alert = store.snapshot().alert
                if (alert != null && alert.kind == ClockAlertKind.TIMER) {
                    store.setTimer(Clock.runAgain(alert, System.currentTimeMillis()))
                }
                finishAlert()
                ClockScheduler.sync(this, ClockStore.get(this).snapshot())
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                val store = ClockStore.get(this)
                val alert = store.snapshot().alert
                val alarm = alert?.alarmId?.let { id -> store.snapshot().alarms.find { it.id == id } }
                if (alarm != null) {
                    store.replaceAlarm(Clock.snooze(alarm, System.currentTimeMillis()))
                }
                finishAlert()
                ClockScheduler.sync(this, ClockStore.get(this).snapshot())
                return START_NOT_STICKY
            }
        }
        val alert = ClockStore.get(this).snapshot().alert
        if (alert == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = notification(this, alert)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFY,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFY, notification)
        }
        if (!ClockSoundPlayer.alerting) startTone()
        return START_STICKY
    }

    override fun onDestroy() {
        ClockSoundPlayer.stop()
        super.onDestroy()
    }

    private fun finishAlert() {
        ClockStore.get(this).setAlert(null)
        ClockSoundPlayer.stop()
        stopSelf()
    }

    private fun startTone() {
        ClockSoundPlayer.startAlert(this, SettingsRepository(this).settings.value.clockSound)
    }

    companion object {
        const val CHANNEL = "clock"
        const val NOTIFY = 7103
        const val ACTION_STOP = "xyz.cdr.builderlauncher.clock.STOP_ALERT"
        const val ACTION_DISMISS = "xyz.cdr.builderlauncher.clock.DISMISS_ALERT"
        const val ACTION_RUN_AGAIN = "xyz.cdr.builderlauncher.clock.RUN_AGAIN"
        const val ACTION_SNOOZE = "xyz.cdr.builderlauncher.clock.SNOOZE"
        const val EXTRA_ALERT = "clock_alert"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, ClockAlertService::class.java),
            )
        }

        fun stop(context: Context) {
            context.applicationContext.startService(
                Intent(context.applicationContext, ClockAlertService::class.java).setAction(ACTION_STOP),
            )
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL, "Clock", NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(null, null)
            channel.enableVibration(true)
            nm.createNotificationChannel(channel)
        }

        fun notification(context: Context, alert: ClockAlert): Notification {
            ensureChannel(context)
            val open = activityIntent(context)
            val title = when (alert.kind) {
                ClockAlertKind.TIMER -> "Time is up"
                ClockAlertKind.ALARM -> alert.label.ifBlank { "Alarm" }
            }
            val body = when (alert.kind) {
                ClockAlertKind.TIMER -> Clock.formatTimer(alert.durationMs)
                ClockAlertKind.ALARM -> Clock.formatAlarm(alert.hour, alert.minute)
            }
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setSound(null)
            if (alert.kind == ClockAlertKind.TIMER) {
                builder.addAction(0, "stop", serviceIntent(context, ACTION_STOP, 11))
                builder.addAction(0, "run again", serviceIntent(context, ACTION_RUN_AGAIN, 12))
            } else {
                builder.addAction(0, "dismiss", serviceIntent(context, ACTION_DISMISS, 13))
                builder.addAction(0, "snooze 8 min", serviceIntent(context, ACTION_SNOOZE, 14))
            }
            return builder.build()
        }

        private fun activityIntent(context: Context): PendingIntent {
            return PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_ALERT, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun serviceIntent(context: Context, action: String, code: Int): PendingIntent {
            return PendingIntent.getService(
                context,
                code,
                Intent(context, ClockAlertService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

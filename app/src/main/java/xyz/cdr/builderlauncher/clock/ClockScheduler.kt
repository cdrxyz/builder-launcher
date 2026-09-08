package xyz.cdr.builderlauncher.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object ClockScheduler {
    const val ACTION_TIMER = "xyz.cdr.builderlauncher.clock.TIMER"
    const val ACTION_ALARM = "xyz.cdr.builderlauncher.clock.ALARM"
    const val EXTRA_ALARM_ID = "alarm_id"

    fun sync(context: Context, snapshot: ClockSnapshot, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        cancel(app, timerRequest(app))
        if (snapshot.timer.running) {
            val ends = snapshot.timer.endsAt
            if (ends != null && ends > now) schedule(app, timerRequest(app), ends)
        }
        snapshot.alarms.forEach { alarm ->
            val req = alarmRequest(app, alarm.id)
            cancel(app, req)
            if (alarm.enabled) {
                schedule(app, req, Clock.nextFireAt(alarm, now))
            }
        }
    }

    fun cancelTimer(context: Context) {
        cancel(context.applicationContext, timerRequest(context.applicationContext))
    }

    private fun schedule(context: Context, request: PendingIntent, at: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        if (canExact) {
            runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, request) }
                .onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, request) }
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, request)
        }
    }

    private fun cancel(context: Context, request: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(request)
        request.cancel()
    }

    private fun timerRequest(context: Context): PendingIntent {
        val intent = Intent(context, ClockReceiver::class.java).setAction(ACTION_TIMER)
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmRequest(context: Context, id: String): PendingIntent {
        val intent = Intent(context, ClockReceiver::class.java)
            .setAction(ACTION_ALARM)
            .putExtra(EXTRA_ALARM_ID, id)
        val code = 1000 + (id.hashCode() and 0x0fff)
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

package xyz.cdr.builderlauncher.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import xyz.cdr.builderlauncher.MainActivity

object ClockScheduler {
    const val ACTION_TIMER = "xyz.cdr.builderlauncher.clock.TIMER"
    const val ACTION_ALARM = "xyz.cdr.builderlauncher.clock.ALARM"
    const val EXTRA_ALARM_ID = "alarm_id"

    fun reconcile(context: Context, store: ClockStore, now: Long = System.currentTimeMillis()) {
        val snap = store.snapshot()
        if (snap.alert == null && Clock.overdueTimer(snap.timer, now)) {
            val fired = Clock.fireTimer(snap.timer)
            store.setTimer(fired.timer)
            store.setAlert(fired.alert)
        }
        if (store.snapshot().alert == null) {
            store.snapshot().alarms.forEach { alarm ->
                if (store.snapshot().alert == null && Clock.catchUpDue(alarm, now)) {
                    val fired = Clock.fireAlarm(alarm, now)
                    store.replaceAlarm(fired.alarm)
                    store.setAlert(fired.alert)
                }
            }
        }
        sync(context, store.snapshot(), now)
        if (store.snapshot().alert != null) ClockAlertService.start(context)
    }

    fun sync(context: Context, snapshot: ClockSnapshot, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(timerRequest(app))
        if (snapshot.timer.running) {
            val ends = snapshot.timer.endsAt
            if (ends != null && ends > now) scheduleAlarm(app, am, timerRequest(app), ends)
        }
        snapshot.deletedAlarmIds.forEach { id ->
            cancelAlarm(app, am, id)
        }
        val dropped = snapshot.deletedAlarmIds.toSet()
        snapshot.alarms.forEach { alarm ->
            cancelAlarm(app, am, alarm.id)
            if (alarm.enabled && alarm.id !in dropped) {
                scheduleAlarm(app, am, alarmRequest(app, alarm.id), Clock.nextFireAt(alarm, now))
            }
        }
    }

    fun cancelTimer(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(timerRequest(app))
    }

    private fun scheduleAlarm(context: Context, am: AlarmManager, request: PendingIntent, at: Long) {
        val show = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(ClockAlertService.EXTRA_ALERT, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching { am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), request) }
            .onFailure { scheduleExact(am, request, at) }
    }

    private fun scheduleExact(am: AlarmManager, request: PendingIntent, at: Long) {
        val canExact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        if (canExact) {
            runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, request) }
                .onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, request) }
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, request)
        }
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

    private fun cancelAlarm(context: Context, am: AlarmManager, id: String) {
        am.cancel(alarmRequest(context, id))
        am.cancel(legacyAlarmRequest(context, id))
    }

    private fun alarmRequest(context: Context, id: String): PendingIntent {
        val intent = Intent(context, ClockReceiver::class.java)
            .setAction(ACTION_ALARM)
            .setData(Uri.parse("xyz.cdr.builderlauncher://alarm/$id"))
            .putExtra(EXTRA_ALARM_ID, id)
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun legacyAlarmRequest(context: Context, id: String): PendingIntent {
        val intent = Intent(context, ClockReceiver::class.java)
            .setAction(ACTION_ALARM)
            .putExtra(EXTRA_ALARM_ID, id)
        return PendingIntent.getBroadcast(
            context,
            1000 + (id.hashCode() and 0x0fff),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

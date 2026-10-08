package xyz.cdr.builderlauncher.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ClockStore.get(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                ClockScheduler.reconcile(context, store)
            }
            ClockScheduler.ACTION_TIMER -> {
                val now = System.currentTimeMillis()
                val snap = Clock.normalize(store.snapshot(), now)
                val id = intent.getStringExtra(ClockScheduler.EXTRA_TIMER_ID)
                val timer = snap.timers.find { it.id == id } ?: Clock.overdueTimers(snap.timers, now).firstOrNull()
                if (timer != null && Clock.overdueTimer(timer, now) && snap.alert == null) {
                    val fired = Clock.fireTimer(timer)
                    store.setTimer(fired.timer)
                    store.setAlert(fired.alert)
                    ClockAlertService.start(context)
                }
                ClockScheduler.sync(context, store.snapshot())
            }
            ClockScheduler.ACTION_ALARM -> {
                val now = System.currentTimeMillis()
                val id = intent.getStringExtra(ClockScheduler.EXTRA_ALARM_ID)
                val alarm = store.snapshot().alarms.find { it.id == id }
                if (alarm != null && !Clock.occurrenceHandled(alarm, now)) {
                    val fired = Clock.fireAlarm(alarm, now)
                    store.replaceAlarm(fired.alarm)
                    store.setAlert(fired.alert)
                }
                ClockScheduler.sync(context, store.snapshot())
                if (store.snapshot().alert != null) ClockAlertService.start(context)
            }
        }
        if (ClockAlertLock.shouldLaunch(store.snapshot().alert != null, ClockAlertLock.keyguardLocked(context))) {
            ClockAlertLock.tryLaunch(context)
        }
    }
}

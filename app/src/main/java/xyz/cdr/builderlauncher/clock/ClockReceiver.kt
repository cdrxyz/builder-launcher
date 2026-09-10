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
                val fired = Clock.fireTimer(store.snapshot().timer)
                store.setTimer(fired.timer)
                store.setAlert(fired.alert)
                ClockAlertService.start(context)
                ClockScheduler.sync(context, store.snapshot())
            }
            ClockScheduler.ACTION_ALARM -> {
                val id = intent.getStringExtra(ClockScheduler.EXTRA_ALARM_ID)
                val alarm = store.snapshot().alarms.find { it.id == id }
                if (alarm != null) {
                    val fired = Clock.fireAlarm(alarm, System.currentTimeMillis())
                    store.replaceAlarm(fired.alarm)
                    store.setAlert(fired.alert)
                }
                ClockScheduler.sync(context, store.snapshot())
                if (store.snapshot().alert != null) ClockAlertService.start(context)
            }
        }
    }
}

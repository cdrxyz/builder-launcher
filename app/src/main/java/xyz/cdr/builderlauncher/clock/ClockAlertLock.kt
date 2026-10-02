package xyz.cdr.builderlauncher.clock

import android.app.Activity
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.core.content.getSystemService

object ClockAlertLock {
    val activityClass: Class<out Activity> = ClockAlertActivity::class.java

    data class Window(
        val showWhenLocked: Boolean,
        val turnScreenOn: Boolean,
        val keepScreenOn: Boolean,
    )

    fun window(alerting: Boolean) = Window(
        showWhenLocked = alerting,
        turnScreenOn = alerting,
        keepScreenOn = alerting,
    )

    fun intentFlags(): Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_NO_USER_ACTION or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT

    fun shouldLaunch(alerting: Boolean, keyguardLocked: Boolean): Boolean = alerting

    fun keyguardLocked(context: Context): Boolean {
        val keyguard = context.applicationContext.getSystemService<KeyguardManager>()
        return keyguard?.isKeyguardLocked == true
    }

    fun activityIntent(context: Context): Intent =
        Intent(context, activityClass)
            .addFlags(intentFlags())
            .putExtra(ClockAlertService.EXTRA_ALERT, true)

    fun tryLaunch(context: Context) {
        val app = context.applicationContext
        val intent = activityIntent(app)
        val started = runCatching {
            app.startActivity(intent)
            true
        }.getOrDefault(false)
        if (started) return
        runCatching {
            PendingIntent.getActivity(
                app,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ).send()
        }
    }

    fun apply(activity: Activity, alerting: Boolean) {
        val next = window(alerting)
        if (Build.VERSION.SDK_INT >= 27) {
            activity.setShowWhenLocked(next.showWhenLocked)
            activity.setTurnScreenOn(next.turnScreenOn)
        } else {
            @Suppress("DEPRECATION")
            val legacy = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            if (alerting) activity.window.addFlags(legacy) else activity.window.clearFlags(legacy)
        }
        if (next.keepScreenOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

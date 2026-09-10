package xyz.cdr.builderlauncher.clock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import xyz.cdr.builderlauncher.data.SettingsRepository
import xyz.cdr.builderlauncher.ui.ClockAlertScreen
import xyz.cdr.builderlauncher.ui.theme.BuilderTheme
import xyz.cdr.builderlauncher.ui.theme.accentColor

class ClockAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClockAlertLock.apply(this, alerting = true)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        val clock = ClockStore.get(this)
        if (clock.snapshot().alert == null) {
            finish()
            return
        }
        ClockAlertService.start(this)
        val settings = SettingsRepository(this)
        setContent {
            val current by settings.settings.collectAsState()
            val clockState by clock.state.collectAsState()
            val alert = clockState.alert
            BuilderTheme(theme = current.uiTheme, accent = accentColor(current.accentHex)) {
                if (alert == null) {
                    LaunchedEffect(Unit) { finish() }
                } else {
                    ClockAlertScreen(
                        alert = alert,
                        sound = current.clockSound,
                        onStop = { ClockAlertService.stop(this@ClockAlertActivity) },
                        onRunAgain = { send(ClockAlertService.ACTION_RUN_AGAIN) },
                        onDismiss = { send(ClockAlertService.ACTION_DISMISS) },
                        onSnooze = { send(ClockAlertService.ACTION_SNOOZE) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ClockAlertLock.apply(this, alerting = ClockStore.get(this).snapshot().alert != null)
        if (ClockStore.get(this).snapshot().alert == null) finish()
    }

    private fun send(action: String) {
        startService(Intent(this, ClockAlertService::class.java).setAction(action))
    }
}

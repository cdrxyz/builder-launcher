package xyz.cdr.builderlauncher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import xyz.cdr.builderlauncher.ai.LlmClient
import xyz.cdr.builderlauncher.apps.InstalledApps
import xyz.cdr.builderlauncher.commands.CommandExecutor
import xyz.cdr.builderlauncher.data.LocalLists
import xyz.cdr.builderlauncher.data.PinnedApps
import xyz.cdr.builderlauncher.data.SettingsRepository
import xyz.cdr.builderlauncher.ui.BuilderRoot
import xyz.cdr.builderlauncher.ui.theme.BuilderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 1)
        }
        val settings = SettingsRepository(this)
        val apps = InstalledApps(this)
        val lists = LocalLists(this)
        val pins = PinnedApps(this)
        val llm = LlmClient(settings)
        val executor = CommandExecutor(this, apps, lists)
        setContent {
            BuilderTheme {
                BuilderRoot(
                    settingsRepo = settings,
                    apps = apps,
                    lists = lists,
                    pins = pins,
                    llm = llm,
                    executor = executor,
                )
            }
        }
    }
}

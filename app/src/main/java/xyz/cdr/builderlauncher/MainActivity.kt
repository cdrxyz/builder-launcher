package xyz.cdr.builderlauncher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import xyz.cdr.builderlauncher.ai.LlmClient
import xyz.cdr.builderlauncher.ai.oauth.OAuthService
import xyz.cdr.builderlauncher.apps.InstalledApps
import xyz.cdr.builderlauncher.commands.CommandExecutor
import xyz.cdr.builderlauncher.contacts.PhoneContacts
import xyz.cdr.builderlauncher.sms.SmsSender
import xyz.cdr.builderlauncher.data.LocalLists
import xyz.cdr.builderlauncher.data.ChatStore
import xyz.cdr.builderlauncher.data.PinnedApps
import xyz.cdr.builderlauncher.data.SettingsRepository
import xyz.cdr.builderlauncher.home.HomeRole
import xyz.cdr.builderlauncher.ui.BuilderRoot
import xyz.cdr.builderlauncher.ui.theme.BuilderTheme
import xyz.cdr.builderlauncher.ui.theme.accentColor
import xyz.cdr.builderlauncher.weather.WeatherRepository
import xyz.cdr.builderlauncher.stocks.StocksRepository

class MainActivity : ComponentActivity() {
    private val homeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        val needed = listOf(Manifest.permission.READ_CONTACTS)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
        }
        val settings = SettingsRepository(this)
        val apps = InstalledApps(this)
        val lists = LocalLists(this)
        val chats = ChatStore(this)
        val pins = PinnedApps(this)
        val people = PhoneContacts(this)
        val sms = SmsSender(this)
        val oauth = OAuthService(settings)
        val llm = LlmClient(settings, oauth)
        val executor = CommandExecutor(this, apps, lists, pins, people, sms)
        val weather = WeatherRepository(this, settings)
        val stocks = StocksRepository(this)
        setContent {
            val current by settings.settings.collectAsState()
            BuilderTheme(accent = accentColor(current.accentHex)) {
                BuilderRoot(
                    settingsRepo = settings,
                    apps = apps,
                    lists = lists,
                    chats = chats,
                    pins = pins,
                    contacts = people,
                    llm = llm,
                    oauth = oauth,
                    executor = executor,
                    weather = weather,
                    stocks = stocks,
                    onRequestHome = { askToBeHome(fromSettings = true) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        askToBeHome(fromSettings = false)
    }

    private fun askToBeHome(fromSettings: Boolean) {
        val prefs = getSharedPreferences(HomeRole.PREFS, MODE_PRIVATE)
        val asked = prefs.getBoolean(HomeRole.ASKED, false)
        val held = HomeRole.isHeld(this)
        if (!fromSettings && !HomeRole.shouldAsk(asked, held)) return
        homeRoleLauncher.launch(HomeRole.requestIntent(this))
        prefs.edit().putBoolean(HomeRole.ASKED, true).apply()
    }
}

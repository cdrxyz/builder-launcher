package xyz.cdr.builderlauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.cdr.builderlauncher.ai.AiPlatforms
import xyz.cdr.builderlauncher.ai.HermesUrls
import xyz.cdr.builderlauncher.ai.oauth.OAuthTokens
import xyz.cdr.builderlauncher.backup.BackupFrequency
import xyz.cdr.builderlauncher.backup.BackupSettings
import xyz.cdr.builderlauncher.clock.ClockSound

enum class LlmProvider {
    HERMES, XAI, OPENAI, ANTHROPIC, GEMINI, OPENROUTER, GROQ, DEEPSEEK, MISTRAL, LMSTUDIO, OLLAMA, GENERIC,
}

data class BuilderSettings(
    val provider: LlmProvider = LlmProvider.HERMES,
    val hermesBaseUrl: String = "",
    val hermesOpenInHermex: Boolean = false,
    val hermesWebUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val keyboardMode: KeyboardMode = KeyboardMode.AUTO,
    val commandBarBottom: Boolean? = null,
    val weatherPlace: String = "",
    val weatherLat: Double? = null,
    val weatherLon: Double? = null,
    val weatherUnits: WeatherUnits = WeatherUnits.METRIC,
    val oauthAccess: String = "",
    val oauthRefresh: String = "",
    val oauthExpiresAtEpochMs: Long = 0L,
    val oauthAccount: String = "",
    val accentHex: String = AccentColor.DEFAULT_HEX,
    val stockInsert: StockInsert = StockInsert.TOP,
    val clockSound: ClockSound = ClockSound.PULSE,
    val appIcons: AppIcons = AppIcons.PLAINTEXT,
    val pinUsage: Boolean = false,
    val clockFace: ClockFace = ClockFace.ANALOG,
    val s3Endpoint: String = "",
    val s3Bucket: String = "",
    val s3AccessKey: String = "",
    val s3SecretKey: String = "",
    val s3EncryptionKey: String = "",
    val backupFrequency: BackupFrequency = BackupFrequency.OFF,
    val lastBackupAtEpochMs: Long = 0L,
    val backupIncludeAiCredentials: Boolean = false,
) {
    val signedIn: Boolean get() = oauthAccess.isNotBlank() || oauthRefresh.isNotBlank()

    fun clearedOAuth(): BuilderSettings = copy(
        oauthAccess = "",
        oauthRefresh = "",
        oauthExpiresAtEpochMs = 0L,
        oauthAccount = "",
    )
}

enum class KeyboardMode { AUTO, HARDWARE, SOFTWARE }

enum class StockInsert { TOP, BOTTOM }

enum class AppIcons { PLAINTEXT, ICONS }

enum class ClockFace { ANALOG, DIGITAL }

enum class WeatherUnits {
    METRIC,
    IMPERIAL,
    ;

    fun displayTemperature(celsius: Int): Int =
        if (this == IMPERIAL) kotlin.math.round(celsius * 9.0 / 5.0 + 32.0).toInt() else celsius

    fun displayWind(kmh: Double): Int =
        if (this == IMPERIAL) kotlin.math.round(kmh * 0.621371).toInt() else kotlin.math.round(kmh).toInt()
}

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = createPrefs(appContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<BuilderSettings> = _settings.asStateFlow()
    private var accounts: Map<String, ProviderAccount> = readAccounts()

    fun update(transform: (BuilderSettings) -> BuilderSettings) {
        val next = transform(_settings.value)
        write(next)
        _settings.value = next
    }

    fun saveOAuth(tokens: OAuthTokens) {
        update { s ->
            s.copy(
                oauthAccess = tokens.accessToken,
                oauthRefresh = tokens.refreshToken.ifBlank { s.oauthRefresh },
                oauthExpiresAtEpochMs = tokens.expiresAtEpochMs,
                oauthAccount = tokens.account.ifBlank { s.oauthAccount },
            )
        }
    }

    fun clearOAuth() {
        update { it.clearedOAuth() }
    }

    fun setProvider(provider: LlmProvider) {
        update { current ->
            if (current.provider == provider) current
            else ProviderAccounts.view(accounts, current, provider)
        }
    }

    fun viewAs(provider: LlmProvider): BuilderSettings =
        ProviderAccounts.view(accounts, _settings.value, provider)

    fun connectedProviders(nowMs: Long = System.currentTimeMillis()): List<LlmProvider> =
        ProviderAccounts.connected(accounts, _settings.value, nowMs)

    fun markBackup(atEpochMs: Long) {
        update { it.copy(lastBackupAtEpochMs = atEpochMs) }
    }

    fun applyBackup(restored: BackupSettings) {
        update { current ->
            current.copy(
                provider = restored.provider,
                hermesBaseUrl = restored.hermesBaseUrl,
                hermesOpenInHermex = restored.hermesOpenInHermex,
                hermesWebUrl = restored.hermesWebUrl,
                apiKey = restored.apiKey ?: current.apiKey,
                model = restored.model,
                keyboardMode = restored.keyboardMode,
                weatherPlace = restored.weatherPlace,
                weatherLat = restored.weatherLat,
                weatherLon = restored.weatherLon,
                weatherUnits = restored.weatherUnits,
                accentHex = restored.accentHex.ifBlank { current.accentHex },
                stockInsert = restored.stockInsert,
                clockSound = restored.clockSound,
                appIcons = restored.appIcons,
                pinUsage = restored.pinUsage,
                clockFace = restored.clockFace,
            )
        }
    }

    fun effectiveBaseUrl(snapshot: BuilderSettings = _settings.value): String {
        val platform = AiPlatforms.of(snapshot.provider)
        if (snapshot.provider == LlmProvider.HERMES) return HermesUrls.apiBase(snapshot)
        return platform.apiBase ?: snapshot.hermesBaseUrl.trim().trimEnd('/')
    }

    fun effectiveModel(snapshot: BuilderSettings = _settings.value): String {
        if (snapshot.model.isNotBlank()) return snapshot.model.trim()
        return AiPlatforms.of(snapshot.provider).defaultModel
    }

    private fun read(): BuilderSettings {
        val provider = runCatching {
            LlmProvider.valueOf(prefs.getString(KEY_PROVIDER, LlmProvider.HERMES.name)!!)
        }.getOrDefault(LlmProvider.HERMES)
        val kb = runCatching {
            KeyboardMode.valueOf(prefs.getString(KEY_KB, KeyboardMode.AUTO.name)!!)
        }.getOrDefault(KeyboardMode.AUTO)
        val units = runCatching {
            WeatherUnits.valueOf(prefs.getString(KEY_WEATHER_UNITS, WeatherUnits.METRIC.name)!!)
        }.getOrDefault(WeatherUnits.METRIC)
        val insert = runCatching {
            StockInsert.valueOf(prefs.getString(KEY_STOCK_INSERT, StockInsert.TOP.name)!!)
        }.getOrDefault(StockInsert.TOP)
        val appIcons = runCatching {
            AppIcons.valueOf(prefs.getString(KEY_APP_ICONS, AppIcons.PLAINTEXT.name)!!)
        }.getOrDefault(AppIcons.PLAINTEXT)
        val clockFace = runCatching {
            ClockFace.valueOf(prefs.getString(KEY_CLOCK_FACE, ClockFace.ANALOG.name)!!)
        }.getOrDefault(ClockFace.ANALOG)
        return BuilderSettings(
            provider = provider,
            hermesBaseUrl = prefs.getString(KEY_HERMES, "") ?: "",
            hermesOpenInHermex = prefs.getBoolean(KEY_HERMES_HERMEX, false),
            hermesWebUrl = prefs.getString(KEY_HERMES_WEB, "") ?: "",
            apiKey = prefs.getString(KEY_API, "") ?: "",
            model = prefs.getString(KEY_MODEL, "") ?: "",
            keyboardMode = kb,
            weatherPlace = prefs.getString(KEY_WEATHER_PLACE, "") ?: "",
            weatherLat = prefs.getString(KEY_WEATHER_LAT, "")?.toDoubleOrNull(),
            weatherLon = prefs.getString(KEY_WEATHER_LON, "")?.toDoubleOrNull(),
            weatherUnits = units,
            oauthAccess = prefs.getString(KEY_OAUTH_ACCESS, "") ?: "",
            oauthRefresh = prefs.getString(KEY_OAUTH_REFRESH, "") ?: "",
            oauthExpiresAtEpochMs = prefs.getString(KEY_OAUTH_EXPIRES, "0")?.toLongOrNull() ?: 0L,
            oauthAccount = prefs.getString(KEY_OAUTH_ACCOUNT, "") ?: "",
            accentHex = AccentColor.normalize(prefs.getString(KEY_ACCENT, AccentColor.DEFAULT_HEX)),
            stockInsert = insert,
            clockSound = ClockSound.parse(prefs.getString(KEY_CLOCK_SOUND, ClockSound.PULSE.name)),
            appIcons = appIcons,
            pinUsage = prefs.getBoolean(KEY_PIN_USAGE, false),
            clockFace = clockFace,
            s3Endpoint = prefs.getString(KEY_S3_ENDPOINT, "") ?: "",
            s3Bucket = prefs.getString(KEY_S3_BUCKET, "") ?: "",
            s3AccessKey = prefs.getString(KEY_S3_ACCESS, "") ?: "",
            s3SecretKey = prefs.getString(KEY_S3_SECRET, "") ?: "",
            s3EncryptionKey = prefs.getString(KEY_S3_ENCRYPTION, "") ?: "",
            backupFrequency = BackupFrequency.parse(prefs.getString(KEY_BACKUP_FREQ, BackupFrequency.OFF.name)),
            lastBackupAtEpochMs = prefs.getString(KEY_BACKUP_LAST, "0")?.toLongOrNull() ?: 0L,
            backupIncludeAiCredentials = prefs.getString(KEY_BACKUP_AI, "") == "true",
        )
    }

    private fun readAccounts(): Map<String, ProviderAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, "") ?: return emptyMap()
        if (raw.isBlank()) {
            val s = _settings.value
            return mapOf(s.provider.name to ProviderAccount.of(s))
        }
        return runCatching {
            json.decodeFromString<Map<String, ProviderAccount>>(raw)
        }.getOrDefault(emptyMap())
    }

    private fun write(next: BuilderSettings) {
        accounts = ProviderAccounts.remember(accounts, next)
        prefs.edit()
            .putString(KEY_PROVIDER, next.provider.name)
            .putString(KEY_HERMES, next.hermesBaseUrl)
            .putBoolean(KEY_HERMES_HERMEX, next.hermesOpenInHermex)
            .putString(KEY_HERMES_WEB, next.hermesWebUrl)
            .putString(KEY_API, next.apiKey)
            .putString(KEY_MODEL, next.model)
            .putString(KEY_KB, next.keyboardMode.name)
            .putString(KEY_WEATHER_PLACE, next.weatherPlace)
            .putString(KEY_WEATHER_LAT, next.weatherLat?.toString() ?: "")
            .putString(KEY_WEATHER_LON, next.weatherLon?.toString() ?: "")
            .putString(KEY_WEATHER_UNITS, next.weatherUnits.name)
            .putString(KEY_OAUTH_ACCESS, next.oauthAccess)
            .putString(KEY_OAUTH_REFRESH, next.oauthRefresh)
            .putString(KEY_OAUTH_EXPIRES, next.oauthExpiresAtEpochMs.toString())
            .putString(KEY_OAUTH_ACCOUNT, next.oauthAccount)
            .putString(KEY_ACCENT, AccentColor.normalize(next.accentHex))
            .putString(KEY_STOCK_INSERT, next.stockInsert.name)
            .putString(KEY_CLOCK_SOUND, next.clockSound.name)
            .putString(KEY_APP_ICONS, next.appIcons.name)
            .putBoolean(KEY_PIN_USAGE, next.pinUsage)
            .putString(KEY_CLOCK_FACE, next.clockFace.name)
            .putString(KEY_S3_ENDPOINT, next.s3Endpoint)
            .putString(KEY_S3_BUCKET, next.s3Bucket)
            .putString(KEY_S3_ACCESS, next.s3AccessKey)
            .putString(KEY_S3_SECRET, next.s3SecretKey)
            .putString(KEY_S3_ENCRYPTION, next.s3EncryptionKey)
            .putString(KEY_BACKUP_FREQ, next.backupFrequency.name)
            .putString(KEY_BACKUP_LAST, next.lastBackupAtEpochMs.toString())
            .putString(KEY_BACKUP_AI, if (next.backupIncludeAiCredentials) "true" else "false")
            .putString(KEY_ACCOUNTS, json.encodeToString(accounts))
            .apply()
    }

    companion object {
        const val XAI_BASE = "https://api.x.ai/v1"
        private const val PREFS = "builder.secure"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_HERMES = "hermes_base"
        private const val KEY_HERMES_HERMEX = "hermes_open_hermex"
        private const val KEY_HERMES_WEB = "hermes_web"
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_KB = "keyboard"
        private const val KEY_WEATHER_PLACE = "weather_place"
        private const val KEY_WEATHER_LAT = "weather_lat"
        private const val KEY_WEATHER_LON = "weather_lon"
        private const val KEY_WEATHER_UNITS = "weather_units"
        private const val KEY_OAUTH_ACCESS = "oauth_access"
        private const val KEY_OAUTH_REFRESH = "oauth_refresh"
        private const val KEY_OAUTH_EXPIRES = "oauth_expires"
        private const val KEY_OAUTH_ACCOUNT = "oauth_account"
        private const val KEY_ACCENT = "accent"
        private const val KEY_STOCK_INSERT = "stock_insert"
        private const val KEY_CLOCK_SOUND = "clock_sound"
        private const val KEY_APP_ICONS = "app_icons"
        private const val KEY_PIN_USAGE = "pin_usage"
        private const val KEY_CLOCK_FACE = "clock_face"
        private const val KEY_S3_ENDPOINT = "s3_endpoint"
        private const val KEY_S3_BUCKET = "s3_bucket"
        private const val KEY_S3_ACCESS = "s3_access"
        private const val KEY_S3_SECRET = "s3_secret"
        private const val KEY_S3_ENCRYPTION = "s3_encryption"
        private const val KEY_BACKUP_FREQ = "backup_frequency"
        private const val KEY_BACKUP_LAST = "backup_last"
        private const val KEY_BACKUP_AI = "backup_include_ai"
        private const val KEY_ACCOUNTS = "provider_accounts"

        private fun createPrefs(context: Context): SharedPreferences {
            return try {
                val master = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS,
                    master,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Exception) {
                MemoryPrefs()
            }
        }
    }
}

private class MemoryPrefs : SharedPreferences {
    private val values = mutableMapOf<String, String>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?) = values[key] ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String?, defValue: Int) = defValue
    override fun getLong(key: String?, defValue: Long) = defValue
    override fun getFloat(key: String?, defValue: Float) = defValue
    override fun getBoolean(key: String?, defValue: Boolean) = defValue
    override fun contains(key: String?) = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val staged = mutableMapOf<String, String?>()
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) staged[key] = value
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?) = this
        override fun putInt(key: String?, value: Int) = this
        override fun putLong(key: String?, value: Long) = this
        override fun putFloat(key: String?, value: Float) = this
        override fun putBoolean(key: String?, value: Boolean) = this
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) staged[key] = null
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            values.clear()
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            staged.forEach { (k, v) ->
                if (v == null) values.remove(k) else values[k] = v
            }
            staged.clear()
        }
    }
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

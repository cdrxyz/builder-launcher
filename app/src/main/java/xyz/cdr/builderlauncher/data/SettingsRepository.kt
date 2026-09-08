package xyz.cdr.builderlauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.cdr.builderlauncher.ai.AiPlatforms
import xyz.cdr.builderlauncher.ai.oauth.OAuthTokens

enum class LlmProvider { HERMES, XAI, OPENAI, ANTHROPIC }

data class BuilderSettings(
    val provider: LlmProvider = LlmProvider.HERMES,
    val hermesBaseUrl: String = "",
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

enum class WeatherUnits {
    METRIC,
    IMPERIAL,
    ;

    fun displayTemperature(celsius: Int): Int =
        if (this == IMPERIAL) kotlin.math.round(celsius * 9.0 / 5.0 + 32.0).toInt() else celsius
}

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = createPrefs(appContext)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<BuilderSettings> = _settings.asStateFlow()

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
            else current.copy(provider = provider).clearedOAuth()
        }
    }

    fun effectiveBaseUrl(): String {
        val s = _settings.value
        val platform = AiPlatforms.of(s.provider)
        return platform.apiBase ?: s.hermesBaseUrl.trim().trimEnd('/')
    }

    fun effectiveModel(): String {
        val s = _settings.value
        if (s.model.isNotBlank()) return s.model.trim()
        return AiPlatforms.of(s.provider).defaultModel
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
        return BuilderSettings(
            provider = provider,
            hermesBaseUrl = prefs.getString(KEY_HERMES, "") ?: "",
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
        )
    }

    private fun write(next: BuilderSettings) {
        prefs.edit()
            .putString(KEY_PROVIDER, next.provider.name)
            .putString(KEY_HERMES, next.hermesBaseUrl)
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
            .apply()
    }

    companion object {
        const val XAI_BASE = "https://api.x.ai/v1"
        private const val PREFS = "builder.secure"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_HERMES = "hermes_base"
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

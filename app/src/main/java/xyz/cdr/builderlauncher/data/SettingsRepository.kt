package xyz.cdr.builderlauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LlmProvider { HERMES, XAI }

data class BuilderSettings(
    val provider: LlmProvider = LlmProvider.HERMES,
    val hermesBaseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val keyboardMode: KeyboardMode = KeyboardMode.AUTO,
    val commandBarBottom: Boolean? = null,
)

enum class KeyboardMode { AUTO, HARDWARE, SOFTWARE }

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = createPrefs(appContext)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<BuilderSettings> = _settings.asStateFlow()

    fun update(transform: (BuilderSettings) -> BuilderSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_PROVIDER, next.provider.name)
            .putString(KEY_HERMES, next.hermesBaseUrl)
            .putString(KEY_API, next.apiKey)
            .putString(KEY_MODEL, next.model)
            .putString(KEY_KB, next.keyboardMode.name)
            .apply()
        _settings.value = next
    }

    fun effectiveBaseUrl(): String {
        val s = _settings.value
        return when (s.provider) {
            LlmProvider.XAI -> XAI_BASE
            LlmProvider.HERMES -> s.hermesBaseUrl.trim().trimEnd('/')
        }
    }

    fun effectiveModel(): String {
        val s = _settings.value
        if (s.model.isNotBlank()) return s.model.trim()
        return when (s.provider) {
            LlmProvider.XAI -> "grok-4.6"
            LlmProvider.HERMES -> "default"
        }
    }

    private fun read(): BuilderSettings {
        val provider = runCatching {
            LlmProvider.valueOf(prefs.getString(KEY_PROVIDER, LlmProvider.HERMES.name)!!)
        }.getOrDefault(LlmProvider.HERMES)
        val kb = runCatching {
            KeyboardMode.valueOf(prefs.getString(KEY_KB, KeyboardMode.AUTO.name)!!)
        }.getOrDefault(KeyboardMode.AUTO)
        return BuilderSettings(
            provider = provider,
            hermesBaseUrl = prefs.getString(KEY_HERMES, "") ?: "",
            apiKey = prefs.getString(KEY_API, "") ?: "",
            model = prefs.getString(KEY_MODEL, "") ?: "",
            keyboardMode = kb,
        )
    }

    companion object {
        const val XAI_BASE = "https://api.x.ai/v1"
        private const val PREFS = "builder.secure"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_HERMES = "hermes_base"
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_KB = "keyboard"

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

package xyz.cdr.builderlauncher.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class WeatherSnapshot(
    val temperature: Int,
    val condition: String,
    val fetchedAt: Long,
    val latitude: Double,
    val longitude: Double,
) {
    val line: String get() = "$temperature° $condition"
}

class WeatherRepository(
    context: Context,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    private val app = context.applicationContext
    private val file = File(app.filesDir, "weather.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _current = MutableStateFlow(load())
    val current: StateFlow<WeatherSnapshot?> = _current.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val loc = lastLocation() ?: return@withContext
        val unit = if (Locale.getDefault().country.equals("US", true)) "fahrenheit" else "celsius"
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", loc.latitude.toString())
            .addQueryParameter("longitude", loc.longitude.toString())
            .addQueryParameter("current", "temperature_2m,weather_code")
            .addQueryParameter("temperature_unit", unit)
            .build()
        val req = Request.Builder().url(url).get().build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val raw = resp.body?.string().orEmpty()
                val current = json.parseToJsonElement(raw).jsonObject["current"]?.jsonObject ?: return@use
                val temp = current["temperature_2m"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@use
                val code = current["weather_code"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@use
                persist(
                    WeatherSnapshot(
                        temperature = kotlin.math.round(temp).toInt(),
                        condition = WeatherCodes.label(code),
                        fetchedAt = System.currentTimeMillis(),
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                    ),
                )
            }
        }
    }

    private fun persist(next: WeatherSnapshot) {
        _current.value = next
        file.writeText(json.encodeToString(next))
    }

    private fun load(): WeatherSnapshot? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<WeatherSnapshot>(file.readText()) }.getOrNull()
    }

    private fun lastLocation(): Location? {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
        return providers.mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }
}

package xyz.cdr.builderlauncher.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.cdr.builderlauncher.data.SettingsRepository
import xyz.cdr.builderlauncher.data.WeatherUnits
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

@Serializable
data class WeatherSnapshot(
    val temperature: Int,
    val condition: String,
    val fetchedAt: Long,
    val latitude: Double,
    val longitude: Double,
) {
    val line: String get() = line(WeatherUnits.METRIC)

    fun line(units: WeatherUnits): String = "${units.displayTemperature(temperature)}° $condition"
}

class WeatherRepository(
    context: Context,
    private val settings: SettingsRepository,
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
        val point = WeatherPointResolver.fromSettings(settings.settings.value) ?: gpsPoint() ?: return@withContext
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", point.latitude.toString())
            .addQueryParameter("longitude", point.longitude.toString())
            .addQueryParameter("current", "temperature_2m,weather_code")
            .addQueryParameter("temperature_unit", "celsius")
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
                        latitude = point.latitude,
                        longitude = point.longitude,
                    ),
                )
            }
        }
    }

    suspend fun suggest(query: String): List<WeatherPlace> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val url = Geocoding.SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("name", q)
            .addQueryParameter("count", "6")
            .addQueryParameter("language", "en")
            .addQueryParameter("format", "json")
            .build()
        val req = Request.Builder().url(url).get().build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                Geocoding.parseSearch(resp.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(next: WeatherSnapshot) {
        _current.value = next
        file.writeText(json.encodeToString(next))
    }

    private fun load(): WeatherSnapshot? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<WeatherSnapshot>(file.readText()) }.getOrNull()
    }

    private suspend fun gpsPoint(): WeatherPoint? {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        val last = providers.mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
        val loc = last ?: oneShot(lm, LocationManager.NETWORK_PROVIDER) ?: return null
        return WeatherPoint(latitude = loc.latitude, longitude = loc.longitude, source = "gps")
    }

    private suspend fun oneShot(lm: LocationManager, provider: String): Location? =
        withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { lm.removeUpdates(this) }
                        if (cont.isActive) cont.resume(location)
                    }
                }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
                val started = runCatching {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }
                if (started.isFailure && cont.isActive) cont.resume(null)
            }
        }
}

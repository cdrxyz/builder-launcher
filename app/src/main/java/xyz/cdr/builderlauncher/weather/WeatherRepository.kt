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
    val celsius: Boolean = false,
) {
    fun line(units: WeatherUnits): String = "${units.displayTemperature(temperature)}° $condition"
}

object WeatherCache {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun parse(raw: String): WeatherSnapshot? {
        val snap = runCatching { json.decodeFromString<WeatherSnapshot>(raw) }.getOrNull() ?: return null
        return snap.takeIf { it.celsius }
    }

    fun encode(snap: WeatherSnapshot): String = json.encodeToString(snap)
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
    private val forecastFile = File(app.filesDir, "weather-forecast.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val _current = MutableStateFlow(load())
    val current: StateFlow<WeatherSnapshot?> = _current.asStateFlow()
    private val _forecast = MutableStateFlow(loadForecast())
    val forecast: StateFlow<WeatherForecast?> = _forecast.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val point = WeatherPointResolver.fromSettings(settings.settings.value) ?: gpsPoint() ?: return@withContext
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", point.latitude.toString())
            .addQueryParameter("longitude", point.longitude.toString())
            .addQueryParameter(
                "current",
                "temperature_2m,apparent_temperature,weather_code,relative_humidity_2m,precipitation,wind_speed_10m,wind_direction_10m,wind_gusts_10m,surface_pressure,visibility,cloud_cover,is_day,dew_point_2m",
            )
            .addQueryParameter(
                "hourly",
                "temperature_2m,weather_code,precipitation_probability,uv_index",
            )
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max",
            )
            .addQueryParameter("forecast_days", "7")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("temperature_unit", "celsius")
            .addQueryParameter("wind_speed_unit", "kmh")
            .addQueryParameter("precipitation_unit", "mm")
            .build()
        val req = Request.Builder().url(url).get().build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val raw = resp.body?.string().orEmpty()
                val aqi = fetchAqi(point.latitude, point.longitude)
                val parsed = WeatherForecastParser.parse(raw, fetchedAt = System.currentTimeMillis(), aqi = aqi)
                    ?: return@use
                persistForecast(parsed)
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

    private fun persistForecast(next: WeatherForecast) {
        _forecast.value = next
        _current.value = next.snapshot()
        file.writeText(WeatherCache.encode(next.snapshot()))
        forecastFile.writeText(json.encodeToString(next))
    }

    private fun load(): WeatherSnapshot? {
        if (!file.exists()) return null
        val snap = WeatherCache.parse(file.readText())
        if (snap == null) runCatching { file.delete() }
        return snap
    }

    private fun loadForecast(): WeatherForecast? {
        if (!forecastFile.exists()) return null
        return runCatching { json.decodeFromString<WeatherForecast>(forecastFile.readText()) }.getOrNull()
    }

    private fun fetchAqi(lat: Double, lon: Double): Int? {
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", lat.toString())
            .addQueryParameter("longitude", lon.toString())
            .addQueryParameter("current", "us_aqi,european_aqi")
            .build()
        val req = Request.Builder().url(url).get().build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val current = json.parseToJsonElement(resp.body?.string().orEmpty())
                    .jsonObject["current"]?.jsonObject ?: return@use null
                current["us_aqi"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: current["european_aqi"]?.jsonPrimitive?.content?.toIntOrNull()
            }
        }.getOrNull()
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

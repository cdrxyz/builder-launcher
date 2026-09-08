package xyz.cdr.builderlauncher.weather

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import xyz.cdr.builderlauncher.data.WeatherUnits
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Serializable
data class WeatherNow(
    val temperatureC: Int,
    val feelsC: Int,
    val code: Int,
    val humidity: Int? = null,
    val precipMm: Double? = null,
    val precipProb: Int? = null,
    val windKmh: Double? = null,
    val windDir: Int? = null,
    val gustKmh: Double? = null,
    val pressureHpa: Double? = null,
    val visibilityM: Double? = null,
    val cloud: Int? = null,
    val dewC: Int? = null,
    val uv: Double? = null,
    val isDay: Boolean = true,
)

@Serializable
data class WeatherHour(
    val epochMs: Long,
    val temperatureC: Int,
    val code: Int,
    val precipProb: Int? = null,
    val uv: Double? = null,
)

@Serializable
data class WeatherDay(
    val date: String,
    val code: Int,
    val highC: Int,
    val lowC: Int,
    val precipProb: Int? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val uv: Double? = null,
)

@Serializable
data class WeatherForecast(
    val fetchedAt: Long,
    val latitude: Double,
    val longitude: Double,
    val timezone: String = "UTC",
    val current: WeatherNow,
    val hourly: List<WeatherHour> = emptyList(),
    val daily: List<WeatherDay> = emptyList(),
    val aqi: Int? = null,
) {
    fun snapshot(): WeatherSnapshot = WeatherSnapshot(
        temperature = current.temperatureC,
        condition = WeatherCodes.label(current.code),
        fetchedAt = fetchedAt,
        latitude = latitude,
        longitude = longitude,
        celsius = true,
    )
}

object WeatherForecastParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, fetchedAt: Long = System.currentTimeMillis(), aqi: Int? = null): WeatherForecast? {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val lat = root["latitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = root["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val tz = root["timezone"]?.jsonPrimitive?.contentOrNull ?: "UTC"
        val currentObj = root["current"]?.jsonObject ?: return null
        val temp = currentObj.num("temperature_2m") ?: return null
        val code = currentObj.int("weather_code") ?: return null
        val hourly = parseHourly(root["hourly"]?.jsonObject, tz, fetchedAt)
        val nowHour = hourly.minByOrNull { kotlin.math.abs(it.epochMs - fetchedAt) }
        val current = WeatherNow(
            temperatureC = temp.roundToInt(),
            feelsC = (currentObj.num("apparent_temperature") ?: temp).roundToInt(),
            code = code,
            humidity = currentObj.int("relative_humidity_2m"),
            precipMm = currentObj.num("precipitation"),
            precipProb = nowHour?.precipProb,
            windKmh = currentObj.num("wind_speed_10m"),
            windDir = currentObj.int("wind_direction_10m"),
            gustKmh = currentObj.num("wind_gusts_10m"),
            pressureHpa = currentObj.num("surface_pressure"),
            visibilityM = currentObj.num("visibility"),
            cloud = currentObj.int("cloud_cover"),
            dewC = currentObj.num("dew_point_2m")?.roundToInt(),
            uv = nowHour?.uv ?: currentObj.num("uv_index"),
            isDay = (currentObj.int("is_day") ?: 1) == 1,
        )
        val daily = parseDaily(root["daily"]?.jsonObject)
        if (daily.isEmpty()) return null
        return WeatherForecast(
            fetchedAt = fetchedAt,
            latitude = lat,
            longitude = lon,
            timezone = tz,
            current = current,
            hourly = hourly,
            daily = daily,
            aqi = aqi,
        )
    }

    private fun parseHourly(obj: JsonObject?, tz: String, fetchedAt: Long): List<WeatherHour> {
        if (obj == null) return emptyList()
        val times = obj.strings("time")
        val temps = obj.nums("temperature_2m")
        val codes = obj.ints("weather_code")
        val probs = obj.ints("precipitation_probability")
        val uvs = obj.nums("uv_index")
        val zone = runCatching { ZoneId.of(tz) }.getOrDefault(ZoneId.of("UTC"))
        val out = ArrayList<WeatherHour>(24)
        for (i in times.indices) {
            val epoch = parseLocal(times[i], zone) ?: continue
            if (epoch + 60 * 60_000L < fetchedAt) continue
            val temp = temps.getOrNull(i) ?: continue
            val code = codes.getOrNull(i) ?: continue
            out += WeatherHour(
                epochMs = epoch,
                temperatureC = temp.roundToInt(),
                code = code,
                precipProb = probs.getOrNull(i),
                uv = uvs.getOrNull(i),
            )
            if (out.size >= 24) break
        }
        return out
    }

    private fun parseDaily(obj: JsonObject?): List<WeatherDay> {
        if (obj == null) return emptyList()
        val dates = obj.strings("time")
        val codes = obj.ints("weather_code")
        val highs = obj.nums("temperature_2m_max")
        val lows = obj.nums("temperature_2m_min")
        val probs = obj.ints("precipitation_probability_max")
        val sunrises = obj.strings("sunrise")
        val sunsets = obj.strings("sunset")
        val uvs = obj.nums("uv_index_max")
        return dates.indices.mapNotNull { i ->
            val date = dates.getOrNull(i) ?: return@mapNotNull null
            val code = codes.getOrNull(i) ?: return@mapNotNull null
            val high = highs.getOrNull(i) ?: return@mapNotNull null
            val low = lows.getOrNull(i) ?: return@mapNotNull null
            WeatherDay(
                date = date,
                code = code,
                highC = high.roundToInt(),
                lowC = low.roundToInt(),
                precipProb = probs.getOrNull(i),
                sunrise = sunrises.getOrNull(i)?.let { clockOf(it) },
                sunset = sunsets.getOrNull(i)?.let { clockOf(it) },
                uv = uvs.getOrNull(i),
            )
        }.take(7)
    }

    private fun parseLocal(raw: String, zone: ZoneId): Long? {
        val local = runCatching {
            java.time.LocalDateTime.parse(raw)
        }.getOrNull() ?: return null
        return local.atZone(zone).toInstant().toEpochMilli()
    }

    private fun clockOf(raw: String): String? {
        val t = raw.substringAfter('T', missingDelimiterValue = "").ifBlank { return null }
        val time = runCatching { LocalTime.parse(t.take(5)) }.getOrNull() ?: return null
        return DateTimeFormatter.ofPattern("HH:mm").format(time)
    }

    private fun JsonObject.num(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.strings(key: String): List<String> =
        this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    private fun JsonObject.nums(key: String): List<Double?> =
        this[key]?.jsonArray?.map { it.jsonPrimitive.doubleOrNull } ?: emptyList()
    private fun JsonObject.ints(key: String): List<Int?> =
        this[key]?.jsonArray?.map { it.jsonPrimitive.intOrNull } ?: emptyList()
}

object WeatherFormat {
    private val hourFmt = DateTimeFormatter.ofPattern("HH", Locale.US)
    private val weekdayFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)
    private val dirs = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

    fun hourLabel(epochMs: Long, zone: String): String {
        val z = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault())
        return hourFmt.format(java.time.Instant.ofEpochMilli(epochMs).atZone(z))
    }

    fun weekday(date: String, today: String? = null): String {
        if (today != null && date == today) return "Today"
        val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
        return weekdayFmt.format(d)
    }

    fun compass(deg: Int?): String {
        if (deg == null) return ""
        val i = ((deg % 360) / 22.5).roundToInt() % 16
        return dirs[i]
    }

    fun uvLabel(uv: Double?): String {
        if (uv == null) return "—"
        val n = uv.roundToInt()
        val band = when {
            n <= 2 -> "low"
            n <= 5 -> "mod"
            n <= 7 -> "high"
            n <= 10 -> "vhigh"
            else -> "ext"
        }
        return "$n $band"
    }

    fun aqiLabel(aqi: Int?): String {
        if (aqi == null) return "—"
        val band = when {
            aqi <= 50 -> "good"
            aqi <= 100 -> "mod"
            aqi <= 150 -> "usg"
            aqi <= 200 -> "unh"
            aqi <= 300 -> "vunh"
            else -> "haz"
        }
        return "$aqi $band"
    }

    fun temp(c: Int, units: WeatherUnits): String = "${units.displayTemperature(c)}°"

    fun wind(kmh: Double?, dir: Int?, units: WeatherUnits): String {
        if (kmh == null) return "—"
        val speed = units.displayWind(kmh)
        val unit = if (units == WeatherUnits.IMPERIAL) "mph" else "km/h"
        val c = compass(dir)
        return if (c.isBlank()) "$speed $unit" else "$speed $unit $c"
    }

    fun precipChance(prob: Int?): String = if (prob == null) "—" else "$prob%"

    fun humidity(value: Int?): String = if (value == null) "—" else "$value%"

    fun pressure(hpa: Double?, units: WeatherUnits): String {
        if (hpa == null) return "—"
        return if (units == WeatherUnits.IMPERIAL) {
            "%.2f inHg".format(Locale.US, hpa * 0.02953)
        } else {
            "${hpa.roundToInt()} hPa"
        }
    }

    fun visibility(meters: Double?, units: WeatherUnits): String {
        if (meters == null) return "—"
        return if (units == WeatherUnits.IMPERIAL) {
            val miles = meters / 1609.344
            if (miles >= 10) "${miles.roundToInt()} mi" else "%.1f mi".format(Locale.US, miles)
        } else {
            val km = meters / 1000.0
            if (km >= 10) "${km.roundToInt()} km" else "%.1f km".format(Locale.US, km)
        }
    }

    fun cloud(value: Int?): String = if (value == null) "—" else "$value%"
}

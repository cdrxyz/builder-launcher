package xyz.cdr.builderlauncher.weather

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class WeatherPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

object Geocoding {
    const val SEARCH_URL = "https://geocoding-api.open-meteo.com/v1/search"

    fun parseSearch(raw: String, limit: Int = 6): List<WeatherPlace> {
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        val results = root["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val lat = obj["latitude"]?.jsonPrimitive?.doubleOrNull
            val lon = obj["longitude"]?.jsonPrimitive?.doubleOrNull
            if (name.isBlank() || lat == null || lon == null) return@mapNotNull null
            val admin = obj["admin1"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val country = obj["country"]?.jsonPrimitive?.contentOrNull.orEmpty()
            WeatherPlace(
                name = name,
                latitude = lat,
                longitude = lon,
                label = listOf(name, admin, country).filter { it.isNotBlank() }.distinct().joinToString(", "),
            )
        }.take(limit)
    }
}

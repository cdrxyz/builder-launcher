package xyz.cdr.builderlauncher.weather

import xyz.cdr.builderlauncher.data.BuilderSettings

data class WeatherPoint(
    val latitude: Double,
    val longitude: Double,
    val source: String,
)

object WeatherPointResolver {
    fun fromSettings(settings: BuilderSettings): WeatherPoint? {
        val lat = settings.weatherLat ?: return null
        val lon = settings.weatherLon ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        if (settings.weatherPlace.isBlank()) return null
        return WeatherPoint(latitude = lat, longitude = lon, source = "place")
    }
}

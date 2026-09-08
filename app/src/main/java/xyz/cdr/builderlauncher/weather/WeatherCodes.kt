package xyz.cdr.builderlauncher.weather

import xyz.cdr.builderlauncher.data.WeatherUnits

object WeatherCodes {
    fun label(code: Int): String = when (code) {
        0 -> "clear"
        1, 2 -> "fair"
        3 -> "cloudy"
        45, 48 -> "fog"
        in 51..57 -> "drizzle"
        in 61..67, in 80..82 -> "rain"
        in 71..77, in 85..86 -> "snow"
        in 95..99 -> "storm"
        else -> "—"
    }

    fun line(
        temperature: Int,
        code: Int,
        units: WeatherUnits = WeatherUnits.METRIC,
    ): String = "${units.displayTemperature(temperature)}° ${label(code)}"
}

package xyz.cdr.builderlauncher.weather

import xyz.cdr.builderlauncher.data.WeatherUnits

enum class WeatherKind {
    CLEAR,
    FAIR,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    STORM,
    ;

    companion object {
        fun ofCode(code: Int): WeatherKind? = when (code) {
            0 -> CLEAR
            1, 2 -> FAIR
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67, in 80..82 -> RAIN
            in 71..77, in 85..86 -> SNOW
            in 95..99 -> STORM
            else -> null
        }

        fun ofCondition(line: String): WeatherKind? {
            val key = line.substringAfter('°', missingDelimiterValue = line).trim().lowercase()
            return entries.find { it.name.equals(key, ignoreCase = true) }
        }
    }
}

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

    fun kind(code: Int): WeatherKind? = WeatherKind.ofCode(code)

    fun line(
        temperature: Int,
        code: Int,
        units: WeatherUnits = WeatherUnits.METRIC,
    ): String = "${units.displayTemperature(temperature)}° ${label(code)}"

    fun homeTemperature(line: String): String {
        val i = line.indexOf('°')
        return if (i >= 0) line.take(i + 1).trim() else line.trim()
    }

    fun short(code: Int): String = when (code) {
        0 -> "clr"
        1, 2 -> "fair"
        3 -> "cld"
        45, 48 -> "fog"
        in 51..57 -> "drz"
        in 61..67, in 80..82 -> "rain"
        in 71..77, in 85..86 -> "snow"
        in 95..99 -> "strm"
        else -> "—"
    }
}

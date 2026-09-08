package xyz.cdr.builderlauncher.weather

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.cdr.builderlauncher.data.WeatherUnits

class WeatherUnitsTest {
    @Test
    fun metricKeepsCelsius() {
        assertEquals(18, WeatherUnits.METRIC.displayTemperature(18))
        assertEquals("18° cloudy", WeatherCodes.line(18, 3, WeatherUnits.METRIC))
    }

    @Test
    fun imperialConvertsFromCelsius() {
        assertEquals(64, WeatherUnits.IMPERIAL.displayTemperature(18))
        assertEquals(-4, WeatherUnits.IMPERIAL.displayTemperature(-20))
        assertEquals("64° cloudy", WeatherCodes.line(18, 3, WeatherUnits.IMPERIAL))
    }

    @Test
    fun snapshotLineUsesSelectedUnits() {
        val snap = WeatherSnapshot(
            temperature = 18,
            condition = "cloudy",
            fetchedAt = 0L,
            latitude = 43.45,
            longitude = -80.49,
            celsius = true,
        )
        assertEquals("18° cloudy", snap.line(WeatherUnits.METRIC))
        assertEquals("64° cloudy", snap.line(WeatherUnits.IMPERIAL))
    }

    @Test
    fun dropsLegacyCacheWithoutCelsiusFlag() {
        val raw = """
            {"temperature":64,"condition":"cloudy","fetchedAt":1,"latitude":40.7,"longitude":-74.0}
        """.trimIndent()
        assertEquals(null, WeatherCache.parse(raw))
    }

    @Test
    fun keepsCelsiusCache() {
        val raw = """
            {"temperature":18,"condition":"cloudy","fetchedAt":1,"latitude":43.45,"longitude":-80.49,"celsius":true}
        """.trimIndent()
        val snap = WeatherCache.parse(raw)!!
        assertEquals(18, snap.temperature)
        assertEquals("18° cloudy", snap.line(WeatherUnits.METRIC))
    }
}

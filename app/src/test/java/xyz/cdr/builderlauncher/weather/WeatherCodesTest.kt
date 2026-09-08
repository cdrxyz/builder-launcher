package xyz.cdr.builderlauncher.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodesTest {
    @Test
    fun commonCodes() {
        assertEquals("clear", WeatherCodes.label(0))
        assertEquals("fair", WeatherCodes.label(1))
        assertEquals("cloudy", WeatherCodes.label(3))
        assertEquals("fog", WeatherCodes.label(45))
        assertEquals("drizzle", WeatherCodes.label(51))
        assertEquals("rain", WeatherCodes.label(61))
        assertEquals("rain", WeatherCodes.label(80))
        assertEquals("snow", WeatherCodes.label(71))
        assertEquals("storm", WeatherCodes.label(95))
        assertEquals("—", WeatherCodes.label(1234))
    }

    @Test
    fun kindFromCode() {
        assertEquals(WeatherKind.CLEAR, WeatherKind.ofCode(0))
        assertEquals(WeatherKind.FAIR, WeatherKind.ofCode(2))
        assertEquals(WeatherKind.CLOUDY, WeatherKind.ofCode(3))
        assertEquals(WeatherKind.FOG, WeatherKind.ofCode(45))
        assertEquals(WeatherKind.DRIZZLE, WeatherKind.ofCode(51))
        assertEquals(WeatherKind.RAIN, WeatherKind.ofCode(80))
        assertEquals(WeatherKind.SNOW, WeatherKind.ofCode(71))
        assertEquals(WeatherKind.STORM, WeatherKind.ofCode(95))
        assertEquals(null, WeatherKind.ofCode(1234))
    }

    @Test
    fun kindFromHomeLine() {
        assertEquals(WeatherKind.CLOUDY, WeatherKind.ofCondition("18° cloudy"))
        assertEquals(WeatherKind.SNOW, WeatherKind.ofCondition("snow"))
        assertEquals(null, WeatherKind.ofCondition("18° —"))
    }

    @Test
    fun lineFormat() {
        assertEquals("18° cloudy", WeatherCodes.line(18, 3))
        assertEquals("-2° snow", WeatherCodes.line(-2, 71))
    }
}

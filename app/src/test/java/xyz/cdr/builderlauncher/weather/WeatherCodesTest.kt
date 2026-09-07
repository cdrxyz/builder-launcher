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
    fun lineFormat() {
        assertEquals("18° cloudy", WeatherCodes.line(18, 3))
        assertEquals("-2° snow", WeatherCodes.line(-2, 71))
    }
}

package xyz.cdr.builderlauncher.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.data.WeatherUnits

class WeatherForecastParserTest {
    private val raw = """
        {
          "latitude": 43.45,
          "longitude": -80.49,
          "timezone": "America/Toronto",
          "current": {
            "time": "2026-09-08T12:00",
            "temperature_2m": 18.2,
            "apparent_temperature": 16.4,
            "weather_code": 3,
            "relative_humidity_2m": 64,
            "precipitation": 0.0,
            "wind_speed_10m": 12.4,
            "wind_direction_10m": 270,
            "wind_gusts_10m": 22.0,
            "surface_pressure": 1013.2,
            "visibility": 24100,
            "cloud_cover": 80,
            "is_day": 1,
            "dew_point_2m": 11.1
          },
          "hourly": {
            "time": ["2026-09-08T12:00","2026-09-08T13:00"],
            "temperature_2m": [18.2, 19.0],
            "weather_code": [3, 61],
            "precipitation_probability": [40, 70],
            "uv_index": [4.2, 5.1]
          },
          "daily": {
            "time": ["2026-09-08","2026-09-09"],
            "weather_code": [3, 61],
            "temperature_2m_max": [22.1, 18.0],
            "temperature_2m_min": [11.0, 10.0],
            "precipitation_probability_max": [40, 80],
            "sunrise": ["2026-09-08T06:42","2026-09-09T06:43"],
            "sunset": ["2026-09-08T19:51","2026-09-09T19:49"],
            "uv_index_max": [5.4, 3.1]
          }
        }
    """.trimIndent()

    @Test
    fun parsesOpenMeteoForecast() {
        val fetched = java.time.ZonedDateTime.of(
            2026, 9, 8, 12, 0, 0, 0,
            java.time.ZoneId.of("America/Toronto"),
        ).toInstant().toEpochMilli()
        val forecast = WeatherForecastParser.parse(raw, fetchedAt = fetched, aqi = 42)
        assertNotNull(forecast)
        val f = forecast!!
        assertEquals(18, f.current.temperatureC)
        assertEquals(16, f.current.feelsC)
        assertEquals("cloudy", WeatherCodes.label(f.current.code))
        assertEquals(64, f.current.humidity)
        assertEquals(40, f.current.precipProb)
        assertEquals(270, f.current.windDir)
        assertEquals(2, f.hourly.size)
        assertEquals(2, f.daily.size)
        assertEquals("06:42", f.daily[0].sunrise)
        assertEquals(42, f.aqi)
        assertEquals("18° cloudy", f.snapshot().line(WeatherUnits.METRIC))
        assertEquals("64° cloudy", f.snapshot().line(WeatherUnits.IMPERIAL))
    }

    @Test
    fun formatHelpers() {
        assertEquals("W", WeatherFormat.compass(270))
        assertEquals("4 mod", WeatherFormat.uvLabel(4.2))
        assertEquals("42 good", WeatherFormat.aqiLabel(42))
        assertEquals("12 km/h W", WeatherFormat.wind(12.4, 270, WeatherUnits.METRIC))
        assertTrue(WeatherFormat.pressure(1013.2, WeatherUnits.METRIC).contains("hPa"))
        assertEquals("cld", WeatherCodes.short(3))
        assertEquals("rain", WeatherCodes.short(61))
    }

    @Test
    fun keepsSevenDailyRows() {
        val dates = (8..16).joinToString(",") { "\"2026-09-${it.toString().padStart(2, '0')}\"" }
        val nums = (8..16).joinToString(",") { it.toString() }
        val raw = """
            {
              "latitude": 43.45,
              "longitude": -80.49,
              "timezone": "America/Toronto",
              "current": { "temperature_2m": 18, "weather_code": 3 },
              "hourly": { "time": [], "temperature_2m": [], "weather_code": [] },
              "daily": {
                "time": [$dates],
                "weather_code": [$nums],
                "temperature_2m_max": [$nums],
                "temperature_2m_min": [$nums]
              }
            }
        """.trimIndent()
        val forecast = WeatherForecastParser.parse(raw, fetchedAt = 0L)!!
        assertEquals(WeatherForecast.DAYS, forecast.daily.size)
        assertEquals("2026-09-08", forecast.daily.first().date)
        assertEquals("2026-09-14", forecast.daily.last().date)
    }
}

package xyz.cdr.builderlauncher.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.data.BuilderSettings

class GeocodingTest {
    @Test
    fun parsesOpenMeteoHits() {
        val raw = """
            {"results":[
              {"name":"Kitchener","latitude":43.42537,"longitude":-80.5112,"admin1":"Ontario","country":"Canada"},
              {"name":"Kitchener","latitude":49.0,"longitude":-117.7,"admin1":"British Columbia","country":"Canada"}
            ]}
        """.trimIndent()
        val places = Geocoding.parseSearch(raw)
        assertEquals(2, places.size)
        assertEquals("Kitchener, Ontario, Canada", places[0].label)
        assertEquals(43.42537, places[0].latitude, 0.0001)
        assertEquals("Kitchener, British Columbia, Canada", places[1].label)
    }

    @Test
    fun emptyWhenMissingResults() {
        assertTrue(Geocoding.parseSearch("{}").isEmpty())
        assertTrue(Geocoding.parseSearch("not-json").isEmpty())
    }

    @Test
    fun skipsIncompleteRows() {
        val raw = """{"results":[{"name":"Nowhere"},{"name":"Berlin","latitude":52.52,"longitude":13.4,"country":"Germany"}]}"""
        val places = Geocoding.parseSearch(raw)
        assertEquals(listOf("Berlin, Germany"), places.map { it.label })
    }
}

class WeatherPointResolverTest {
    @Test
    fun placeOverridesGpsRequirement() {
        val s = BuilderSettings(
            weatherPlace = "Kitchener, Ontario, Canada",
            weatherLat = 43.45,
            weatherLon = -80.49,
        )
        val point = WeatherPointResolver.fromSettings(s)!!
        assertEquals(43.45, point.latitude, 0.0)
        assertEquals("place", point.source)
    }

    @Test
    fun ignoresTypedPlaceWithoutCoords() {
        val s = BuilderSettings(weatherPlace = "kit", weatherLat = null, weatherLon = null)
        assertNull(WeatherPointResolver.fromSettings(s))
    }

    @Test
    fun rejectsOutOfRange() {
        val s = BuilderSettings(weatherPlace = "x", weatherLat = 99.0, weatherLon = 0.0)
        assertNull(WeatherPointResolver.fromSettings(s))
    }
}

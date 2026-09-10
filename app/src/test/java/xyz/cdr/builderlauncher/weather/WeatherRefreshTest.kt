package xyz.cdr.builderlauncher.weather

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRefreshTest {
    @Test
    fun forceAlwaysFetches() {
        assertTrue(WeatherRefresh.shouldFetch(now = 100, fetchedAt = 99, force = true))
        assertTrue(WeatherRefresh.shouldFetch(now = 100, fetchedAt = null, force = true))
    }

    @Test
    fun missingCacheFetches() {
        assertTrue(WeatherRefresh.shouldFetch(now = 100, fetchedAt = null, force = false))
    }

    @Test
    fun freshCacheSkips() {
        val now = WeatherRefresh.TTL_MS
        assertFalse(WeatherRefresh.shouldFetch(fetchedAt = 1L, now = now, force = false))
    }

    @Test
    fun staleCacheFetches() {
        val fetched = 1L
        val now = fetched + WeatherRefresh.TTL_MS
        assertTrue(WeatherRefresh.shouldFetch(fetchedAt = fetched, now = now, force = false))
    }
}

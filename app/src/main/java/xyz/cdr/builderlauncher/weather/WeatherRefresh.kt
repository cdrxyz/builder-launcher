package xyz.cdr.builderlauncher.weather

object WeatherRefresh {
    const val TTL_MS = 15 * 60 * 1000L

    fun shouldFetch(fetchedAt: Long?, now: Long, force: Boolean): Boolean {
        if (force) return true
        val at = fetchedAt ?: return true
        return now - at >= TTL_MS
    }
}

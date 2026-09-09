package xyz.cdr.builderlauncher.podcasts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.URL

object PodcastArtwork {
    private val cache = LinkedHashMap<String, Bitmap>(16, 0.75f, true)

    fun get(url: String): Bitmap? {
        val key = url.trim()
        if (key.isEmpty()) return null
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val bmp = runCatching {
            val conn = URL(key).openConnection()
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.getInputStream().use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        synchronized(cache) {
            if (cache.size >= 24) {
                val oldest = cache.keys.firstOrNull()
                if (oldest != null) cache.remove(oldest)
            }
            cache[key] = bmp
        }
        return bmp
    }
}

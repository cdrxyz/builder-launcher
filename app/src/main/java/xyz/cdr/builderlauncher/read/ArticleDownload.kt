package xyz.cdr.builderlauncher.read

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.cdr.builderlauncher.backup.AccountClient
import java.io.ByteArrayOutputStream
import java.io.File

class ArticleDownload(private val context: Context) {
    fun localFile(articleId: String, hash: String, voice: String = Reads.GROK_VOICE): File =
        File(context.cacheDir, Reads.cacheFileName(articleId, voice, hash))

    fun legacyFile(articleId: String, hash: String): File =
        File(context.cacheDir, Reads.legacyCacheFileName(articleId, hash))

    fun playableFile(articleId: String, voice: String, hash: String): File? {
        val local = localFile(articleId, hash, voice)
        if (Reads.localAudioPlayable(local.length())) return local
        if (Reads.voiceId(voice) == Reads.GROK_VOICE) {
            val old = legacyFile(articleId, hash)
            if (Reads.localAudioPlayable(old.length())) return old
        }
        return null
    }

    fun fetch(token: String, articleId: String, hash: String, voice: String = Reads.GROK_VOICE, onProgress: (Long, Long) -> Unit): ByteArray? {
        if (token.isBlank()) return null
        val url = "${AccountClient.ORIGIN}${Reads.storedAudioQuery(articleId, voice = voice, hash = hash, index = 0)}"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "audio/mpeg")
            .build()
        return OkHttpClient().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            val total = body.contentLength().coerceAtLeast(0L)
            val out = ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var received = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    received += n
                    onProgress(received, total)
                }
            }
            out.toByteArray().takeIf { it.size >= 64 }
        }
    }

    fun keepPlayable(file: File, bytes: ByteArray): Boolean {
        if (!Reads.localAudioPlayable(bytes.size.toLong())) return false
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return Reads.localAudioPlayable(file.length())
    }

    fun onDevice(articleId: String, voice: String, hash: String, title: String): Boolean {
        val cache = playableFile(articleId, voice, hash)?.length() ?: 0L
        return Reads.downloadKept(cache, storeSize(Reads.mediaStoreName(title, voice)))
    }

    private fun storeSize(name: String): Long {
        if (name.isBlank() || Build.VERSION.SDK_INT < 29) return 0L
        val projection = arrayOf(MediaStore.Downloads.SIZE)
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(name),
            "${MediaStore.Downloads.DATE_ADDED} DESC",
        )?.use { cursor ->
            val size = cursor.getColumnIndex(MediaStore.Downloads.SIZE)
            if (cursor.moveToFirst()) return cursor.getLong(size)
        }
        return 0L
    }

    fun save(name: String, bytes: ByteArray): Boolean {
        if (bytes.size < 64) return false
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            return true
        }
        val dir = File(context.cacheDir, "articles").apply { mkdirs() }
        File(dir, name).writeBytes(bytes)
        return true
    }
}

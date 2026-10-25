package xyz.cdr.builderlauncher.ui

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.WebSocket
import xyz.cdr.builderlauncher.ai.oauth.CredentialResolver
import xyz.cdr.builderlauncher.backup.AccountClient
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.read.ArticleDownload
import xyz.cdr.builderlauncher.read.PreparedGrok
import xyz.cdr.builderlauncher.read.ReadArticle
import xyz.cdr.builderlauncher.read.Reads
import xyz.cdr.builderlauncher.read.SpeechStream

internal object ArticleSpeechProgress {
    var bytes by mutableStateOf<Map<String, Long>>(emptyMap())

    fun note(id: String, received: Int) {
        val next = received.toLong()
        Handler(Looper.getMainLooper()).post {
            bytes = bytes + (id to next)
        }
    }
}

internal class ArticleSpeech(
    private val scope: CoroutineScope,
    private val context: Context,
    private val settings: () -> BuilderSettings,
    private val voice: () -> String = { Reads.GROK_VOICE },
) {
    private val ready = mutableMapOf<String, PreparedGrok>()
    private val loads = mutableMapOf<String, Deferred<PreparedGrok>>()
    private val sockets = mutableMapOf<String, WebSocket>()

    fun cancel() {
        sockets.values.forEach { runCatching { it.cancel() } }
        sockets.clear()
    }

    fun load(article: ReadArticle, onFirst: ((PreparedGrok) -> Unit)? = null): Deferred<PreparedGrok> {
        ready[article.id]?.let { stored ->
            onFirst?.invoke(stored)
            return scope.async { stored }
        }
        loads[article.id]?.takeIf { it.isActive }?.let { return it }
        val job = scope.async(Dispatchers.IO) {
            val current = settings()
            val spoken = Reads.voiceId(voice())
            val hash = Reads.speechHash(article.text)
            val saver = ArticleDownload(context)
            val cached = saver.playableFile(article.id, spoken, hash)
            val local = cached ?: saver.localFile(article.id, hash, spoken)
            val downloaded = findDownloaded(Reads.mediaStoreName(article.title, spoken))
            if (cached == null && downloaded != null) {
                val copied = runCatching {
                    context.contentResolver.openInputStream(downloaded)?.use { local.writeBytes(it.readBytes()) }
                    Reads.localAudioPlayable(local.length())
                }.getOrDefault(false)
                if (!copied) {
                    return@async PreparedGrok(downloaded, emptyMap(), complete = true).also {
                        ready[article.id] = it
                        onFirst?.invoke(it)
                    }
                }
            }
            if (Reads.localAudioPlayable(local.length())) {
                return@async PreparedGrok(Uri.fromFile(local), emptyMap(), complete = true).also {
                    ready[article.id] = it
                    onFirst?.invoke(it)
                }
            }
            val account = current.accountToken
            val bearer = CredentialResolver.bearer(current) ?: error("Sign in to xAI in settings.")
            val client = AccountClient()
            val stored = if (account.isBlank()) {
                emptyList()
            } else {
                runCatching { client.storedArticleIndexes(account, article.id, spoken, hash) }.getOrDefault(emptyList())
            }
            if (Reads.storedAudioReady(stored)) {
                PreparedGrok(
                    Uri.parse("${AccountClient.ORIGIN}${Reads.storedAudioQuery(article.id, voice = spoken, hash = hash, index = 0)}"),
                    mapOf("Authorization" to "Bearer $account"),
                ).also {
                    ready[article.id] = it
                    onFirst?.invoke(it)
                }
            } else {
                val file = saver.localFile(article.id, hash, spoken)
                val done = CompletableDeferred<Throwable?>()
                var started = false
                var reported = 0
                val socket = SpeechStream().open(bearer, article.text, file, spoken, onBytes = { bytes ->
                    if (bytes - reported >= 32_000) {
                        reported = bytes
                        ArticleSpeechProgress.note(article.id, bytes)
                    }
                    if (!started && Reads.shouldStartSpeech(bytes)) {
                        started = true
                        onFirst?.invoke(PreparedGrok(Uri.fromFile(file), emptyMap(), complete = false))
                    }
                }, onDone = { err -> done.complete(err) })
                sockets[article.id] = socket
                if (!SpeechStream.waitForFirstAudio(file)) {
                    socket.cancel()
                    file.writeBytes(client.fetchXaiSpeech(bearer, article.text, spoken))
                } else {
                    done.await()?.let { throw it }
                    if (file.length() < 64) error("Speech reply was empty.")
                }
                if (account.isNotBlank()) {
                    runCatching { client.storeArticleAudio(account, article.id, spoken, hash, 0, file.readBytes()) }
                }
                PreparedGrok(Uri.fromFile(file), emptyMap()).also { ready[article.id] = it }
            }
        }
        loads[article.id] = job
        return job
    }

    private fun findDownloaded(name: String): Uri? {
        if (name.isBlank()) return null
        val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.SIZE)
        context.contentResolver.query(
            uri,
            projection,
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(name),
            "${MediaStore.Downloads.DATE_ADDED} DESC",
        )?.use { cursor ->
            val id = cursor.getColumnIndex(MediaStore.Downloads._ID)
            val size = cursor.getColumnIndex(MediaStore.Downloads.SIZE)
            while (cursor.moveToNext()) {
                if (cursor.getLong(size) < 64L) continue
                return Uri.withAppendedPath(uri, cursor.getLong(id).toString())
            }
        }
        return null
    }

    suspend fun prepare(article: ReadArticle, onLabel: (String) -> Unit) {
        val current = settings()
        if (!Reads.usesGrokVoice(current.provider == LlmProvider.XAI, CredentialResolver.bearer(current))) return
        withContext(Dispatchers.Main) { onLabel(Reads.audioProgressLabel("preparing", 0, 1)) }
        runCatching { load(article).await() }.onSuccess {
            withContext(Dispatchers.Main) { onLabel(Reads.audioProgressLabel("ready", 1, 1)) }
        }.onFailure { err ->
            if (err is kotlinx.coroutines.CancellationException) throw err
            val label = Reads.audioProgressLabel("failed", error = err.message ?: "Could not prepare audio.")
            withContext(Dispatchers.Main) { onLabel(label) }
        }
    }
}

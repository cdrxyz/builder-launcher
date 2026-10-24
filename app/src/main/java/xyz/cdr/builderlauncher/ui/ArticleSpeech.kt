package xyz.cdr.builderlauncher.ui

import android.content.Context
import android.net.Uri
import android.os.Handler
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
import java.io.File

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
            val hash = Reads.speechHash(article.text)
            val local = ArticleDownload(context).localFile(article.id, hash)
            if (Reads.localAudioPlayable(local.length())) {
                return@async PreparedGrok(Uri.fromFile(local), emptyMap()).also {
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
                runCatching { client.storedArticleIndexes(account, article.id, Reads.GROK_VOICE, hash) }.getOrDefault(emptyList())
            }
            if (Reads.storedAudioReady(stored)) {
                PreparedGrok(
                    Uri.parse("${AccountClient.ORIGIN}${Reads.storedAudioQuery(article.id, hash = hash, index = 0)}"),
                    mapOf("Authorization" to "Bearer $account"),
                ).also {
                    ready[article.id] = it
                    onFirst?.invoke(it)
                }
            } else {
                val file = File(context.cacheDir, "article-${article.id}-$hash.mp3")
                val done = CompletableDeferred<Throwable?>()
                var started = false
                var reported = 0
                val socket = SpeechStream().open(bearer, article.text, file, Reads.GROK_VOICE, onBytes = { bytes ->
                    if (bytes - reported >= 32_000) {
                        reported = bytes
                        ArticleSpeechProgress.note(article.id, bytes)
                    }
                    if (!started && Reads.shouldStartSpeech(bytes)) {
                        started = true
                        onFirst?.invoke(PreparedGrok(Uri.fromFile(file), emptyMap()))
                    }
                }, onDone = { err -> done.complete(err) })
                sockets[article.id] = socket
                if (!SpeechStream.waitForFirstAudio(file)) {
                    socket.cancel()
                    file.writeBytes(client.fetchXaiSpeech(bearer, article.text, Reads.GROK_VOICE))
                } else {
                    done.await()?.let { throw it }
                    if (file.length() < 64) error("Speech reply was empty.")
                }
                if (account.isNotBlank()) {
                    runCatching { client.storeArticleAudio(account, article.id, Reads.GROK_VOICE, hash, 0, file.readBytes()) }
                }
                PreparedGrok(Uri.fromFile(file), emptyMap()).also { ready[article.id] = it }
            }
        }
        loads[article.id] = job
        return job
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

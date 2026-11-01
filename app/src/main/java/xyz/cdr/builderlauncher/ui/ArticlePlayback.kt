package xyz.cdr.builderlauncher.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.cdr.builderlauncher.ai.oauth.CredentialResolver
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.podcasts.PodcastPlaybackService
import xyz.cdr.builderlauncher.videos.VideoMedia
import xyz.cdr.builderlauncher.podcasts.PodcastPlayer
import xyz.cdr.builderlauncher.read.ArticleMedia
import xyz.cdr.builderlauncher.read.ArticlePlaybackService
import xyz.cdr.builderlauncher.read.ArticleControls
import xyz.cdr.builderlauncher.read.ArticleDownload
import xyz.cdr.builderlauncher.read.ArticlePlayer
import xyz.cdr.builderlauncher.read.ChosenSpeaker
import xyz.cdr.builderlauncher.read.ReadArticle
import xyz.cdr.builderlauncher.read.ReadSpeaker
import xyz.cdr.builderlauncher.read.Reads
import xyz.cdr.builderlauncher.read.ReadsRepository
import java.io.File

internal class ArticlePlayback(
    private val scope: CoroutineScope,
    private val context: android.content.Context,
    private val speaker: ReadSpeaker,
    private val articlePlayer: ArticlePlayer,
    private val reads: ReadsRepository,
    private val settings: () -> BuilderSettings,
    private val setAudio: (String, String) -> Unit,
) {
    private val token = intArrayOf(0)
    val speech = ArticleSpeech(scope, context, settings, { reads.voice.value })

    val controls: ArticleControls = ChosenSpeaker(
        speaker,
        articlePlayer,
        { Reads.usesAccountSpeech(settings().accountToken) },
        { article, start, speed -> start(article, start, speed) },
        ::cancel,
        { article ->
            val hash = Reads.speechHash(article.text)
            ArticleDownload(context).playableFile(article.id, reads.voice.value, hash) != null
        },
    )

    fun bind() {
        articlePlayer.onError = { reason ->
            val id = articlePlayer.state.value.articleId
            if (id != null) setAudio(id, Reads.audioProgressLabel("failed", error = reason))
        }
    }

    fun cancel() {
        token[0] += 1
        speech.cancel()
    }

    fun start(article: ReadArticle, startMs: Long, speed: Float) {
        val mine = token[0]
        PodcastPlayer.pause()
        speaker.pause()
        articlePlayer.arm(article, startMs, speed, playing = false)
        scope.launch {
            var live = false
            val prepared = runCatching {
                speech.load(article) { first ->
                    if (mine != token[0]) return@load
                    live = true
                    val growing = Reads.shouldStreamGrowingFile(first.complete) && first.uri.scheme == "file"
                    scope.launch(Dispatchers.Main) {
                        if (mine != token[0]) return@launch
                        if (growing) {
                            articlePlayer.playGrowing(article, File(first.uri.path.orEmpty()), startMs, speed) {
                                speech.stillWriting(article.id)
                            }
                        } else {
                            articlePlayer.play(article, first.uri, first.headers, startMs, speed)
                        }
                        ArticleMedia.note(article.title)
                        ArticlePlaybackService.start(context, article.title)
                        PodcastPlaybackService.stop(context)
                        VideoMedia.stop(context)
                    }
                }.await()
            }
            if (mine != token[0]) return@launch
            prepared.onSuccess { ready ->
                withContext(Dispatchers.Main) {
                    if (!live) articlePlayer.play(article, ready.uri, ready.headers, startMs, speed)
                    ArticleMedia.note(article.title)
                    ArticlePlaybackService.start(context, article.title)
                    PodcastPlaybackService.stop(context)
                    VideoMedia.stop(context)
                    setAudio(article.id, Reads.audioProgressLabel("ready", 1, 1))
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                withContext(Dispatchers.Main) {
                    articlePlayer.pause()
                    setAudio(article.id, Reads.audioProgressLabel("failed", error = err.message ?: "Could not prepare audio."))
                }
            }
        }
    }

    fun prepare(article: ReadArticle) {
        scope.launch {
            speech.prepare(article) { label -> setAudio(article.id, label) }
        }
    }
}

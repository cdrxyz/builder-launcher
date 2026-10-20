package xyz.cdr.builderlauncher.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.cdr.builderlauncher.backup.AccountClient
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.read.ArticleDownload
import xyz.cdr.builderlauncher.read.ReadArticle
import xyz.cdr.builderlauncher.read.Reads

internal class ArticleDownloads(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: () -> BuilderSettings,
    private val audio: () -> Map<String, String>,
    private val setAudio: (Map<String, String>) -> Unit,
    private val transfers: () -> Map<String, Pair<Long, Long>>,
    private val setTransfers: (Map<String, Pair<Long, Long>>) -> Unit,
    private val downloaded: () -> Set<String>,
    private val setDownloaded: (Set<String>) -> Unit,
    private val prepare: (ReadArticle) -> Unit,
) {
    fun label(id: String): String {
        val transfer = transfers()[id]
        return Reads.articleDownloadLabel(audio()[id].orEmpty(), transfer?.first ?: -1L, transfer?.second ?: 0L)
    }

    fun isDownloaded(id: String): Boolean = id in downloaded()

    fun probe(article: ReadArticle) {
        val current = audio()[article.id].orEmpty()
        if (current == "audio ready" || current.startsWith("audio failed") || current.startsWith("preparing")) return
        scope.launch(Dispatchers.IO) {
            val hash = Reads.speechHash(article.text)
            val local = ArticleDownload(context).localFile(article.id, hash)
            val ready = local.length() >= 64L || runCatching {
                val token = settings().accountToken
                token.isNotBlank() && Reads.storedAudioReady(
                    AccountClient().storedArticleIndexes(token, article.id, Reads.GROK_VOICE, hash),
                )
            }.getOrDefault(false)
            if (!ready) return@launch
            withContext(Dispatchers.Main) {
                val latest = audio()[article.id].orEmpty()
                if (latest.startsWith("preparing") || latest.startsWith("audio failed")) return@withContext
                setAudio(audio() + (article.id to Reads.audioProgressLabel("ready", 1, 1)))
            }
        }
    }

    fun queue(article: ReadArticle) {
        if (article.id in transfers() || article.id in downloaded()) return
        if (audio()[article.id] != "audio ready") {
            prepare(article)
            return
        }
        setTransfers(transfers() + (article.id to (0L to 0L)))
        scope.launch(Dispatchers.IO) {
            val hash = Reads.speechHash(article.text)
            val saver = ArticleDownload(context)
            val local = saver.localFile(article.id, hash)
            val bytes = if (local.length() >= 64L) {
                local.readBytes()
            } else {
                saver.fetch(settings().accountToken, article.id, hash) { received, total ->
                    scope.launch(Dispatchers.Main) {
                        setTransfers(transfers() + (article.id to (received to total)))
                    }
                }
            }
            val saved = bytes != null && saver.save(Reads.articleFileName(article.title), bytes)
            withContext(Dispatchers.Main) {
                setTransfers(transfers() - article.id)
                if (saved) {
                    setDownloaded(downloaded() + article.id)
                    Toast.makeText(context, "Downloaded", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
internal fun rememberArticleDownloads(
    articles: List<ReadArticle>,
    audio: Map<String, String>,
    setAudio: (Map<String, String>) -> Unit,
    prepare: (ReadArticle) -> Unit,
    context: Context,
    scope: CoroutineScope,
    settings: () -> BuilderSettings,
): ArticleDownloads {
    var transfers by remember { mutableStateOf<Map<String, Pair<Long, Long>>>(emptyMap()) }
    var saved by remember { mutableStateOf<Set<String>>(emptySet()) }
    val audioNow = remember { mutableStateOf(audio) }
    audioNow.value = audio
    val downloads = remember(context, scope) {
        ArticleDownloads(
            context = context,
            scope = scope,
            settings = settings,
            audio = { audioNow.value },
            setAudio = setAudio,
            transfers = { transfers },
            setTransfers = { transfers = it },
            downloaded = { saved },
            setDownloaded = { saved = it },
            prepare = prepare,
        )
    }
    LaunchedEffect(articles, audio) {
        articles.forEach { downloads.probe(it) }
    }
    return downloads
}

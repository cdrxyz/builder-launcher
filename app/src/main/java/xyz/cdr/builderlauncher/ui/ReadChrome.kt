package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.commands.SlashCommand
import xyz.cdr.builderlauncher.podcasts.PodcastPlayer
import xyz.cdr.builderlauncher.podcasts.Podcasts
import xyz.cdr.builderlauncher.read.ReadArticle
import xyz.cdr.builderlauncher.read.ReadPlayback
import xyz.cdr.builderlauncher.read.ReadProgress
import xyz.cdr.builderlauncher.read.ArticleControls
import xyz.cdr.builderlauncher.read.ReadSpeaker
import xyz.cdr.builderlauncher.read.Reads
import xyz.cdr.builderlauncher.read.ReadsRepository
import xyz.cdr.builderlauncher.ui.theme.Accent
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Loss
import xyz.cdr.builderlauncher.ui.theme.Paper

@Composable
fun ReadListChrome(
    articles: List<ReadArticle>,
    progress: Map<String, ReadProgress>,
    busy: Boolean = false,
    onBack: () -> Unit = {},
    onOpen: (ReadArticle) -> Unit = {},
    audio: Map<String, String> = emptyMap(),
    onDownload: (ReadArticle) -> Unit = {},
    downloadLabel: (ReadArticle) -> String = { "" },
    downloadVisible: (ReadArticle) -> Boolean = { false },
    downloaded: (ReadArticle) -> Boolean = { false },
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = Reads.TITLE,
            leading = { ScreenBack(Reads.BACK, onBack = onBack) },
        )
        Spacer(Modifier.height(8.dp))
        if (busy) {
            Text(Reads.READING, color = Dim, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }
        if (articles.isEmpty() && !busy) {
            Text(Reads.EMPTY, color = Dim)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(articles, key = { _, article -> article.id }) { index, article ->
                val saved = progress[article.id]?.positionMs ?: 0L
                val duration = Reads.durationMs(article.text)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(article) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        "${index + 1}",
                        color = Accent,
                        modifier = Modifier.width(28.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(article.title, color = Paper)
                        if (article.publication.isNotBlank()) {
                            Text(
                                article.publication,
                                color = Dim,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        val label = Reads.positionLabel(saved, duration)
                        if (label.isNotBlank()) {
                            Text(label, color = Dim, style = MaterialTheme.typography.bodyMedium)
                        }
                        val speech = audio[article.id].orEmpty()
                        if (speech.isNotBlank()) {
                            Text(
                                speech,
                                color = if (speech.startsWith("audio failed")) Loss else Dim,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    ArticleDownloadMark(
                        showButton = downloadVisible(article),
                        downloaded = downloaded(article),
                        label = downloadLabel(article),
                        onDownload = { onDownload(article) },
                    )
                }
            }
        }
    }
}

@Composable
fun ReadPlayerChrome(
    publication: String,
    title: String,
    position: String,
    playing: Boolean,
    progress: Float,
    text: String,
    highlightStart: Int = -1,
    highlightEnd: Int = -1,
    onBack: () -> Unit = {},
    onToggle: () -> Unit = {},
    onBack15: () -> Unit = {},
    onForward15: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onOpen: () -> Unit = {},
    onRemove: () -> Unit = {},
    audioLabel: String = "",
    onDownload: () -> Unit = {},
    downloadLabel: String = "",
    downloadVisible: Boolean = false,
    downloaded: Boolean = false,
    speed: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScreenBack(Reads.BACK, onBack = onBack)
            ArticleDownloadMark(showButton = downloadVisible, downloaded = downloaded, label = downloadLabel, onDownload = onDownload)
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (publication.isNotBlank()) {
                Text(publication, color = Dim, style = MaterialTheme.typography.bodyMedium)
            }
            Text(title, color = Paper, style = MaterialTheme.typography.headlineLarge)
            if (audioLabel.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    audioLabel,
                    color = if (audioLabel.startsWith("audio failed")) Loss else Dim,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(position, color = Dim)
                speed()
            }
            Spacer(Modifier.height(12.dp))
            PodcastScrubBar(progress = progress, onSeekFraction = onSeek)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "−15",
                    color = Paper,
                    modifier = Modifier.clickable(onClick = onBack15).padding(vertical = 8.dp),
                )
                PlayPauseIcon(
                    playing = playing,
                    modifier = Modifier
                        .clickable(onClick = onToggle)
                        .padding(vertical = 8.dp)
                        .semantics { contentDescription = if (playing) "pause" else "play" },
                )
                Text(
                    "+15",
                    color = Paper,
                    modifier = Modifier.clickable(onClick = onForward15).padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    Reads.OPEN,
                    color = Accent,
                    modifier = Modifier.clickable(onClick = onOpen).padding(vertical = 8.dp),
                )
                Text(
                    Reads.REMOVE,
                    color = Dim,
                    modifier = Modifier.clickable(onClick = onRemove).padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = highlighted(text, highlightStart, highlightEnd),
                style = MaterialTheme.typography.bodyLarge.copy(color = Paper),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun highlighted(text: String, start: Int, end: Int) = buildAnnotatedString {
    if (start < 0 || end <= start || end > text.length) {
        append(text)
        return@buildAnnotatedString
    }
    if (start > 0) append(text.substring(0, start))
    withStyle(SpanStyle(color = Accent)) { append(text.substring(start, end)) }
    if (end < text.length) append(text.substring(end))
}

sealed interface ReadEvent {
    data object Home : ReadEvent
    data object List : ReadEvent
    data class Open(val id: String) : ReadEvent
    data class Value(val text: String) : ReadEvent
    data class Pick(val glyph: Char) : ReadEvent
    data object Clear : ReadEvent
    data object Submit : ReadEvent
    data class Slash(val cmd: SlashCommand) : ReadEvent
    data class Browser(val url: String) : ReadEvent
}

@Composable
fun ColumnScope.ReadHost(
    player: Boolean,
    articles: List<ReadArticle>,
    progress: Map<String, ReadProgress>,
    busy: Boolean,
    articleId: String?,
    playback: ReadPlayback,
    speed: Float,
    prompt: Char,
    input: String,
    hardware: Boolean,
    reads: ReadsRepository,
    speaker: ArticleControls,
    onEvent: (ReadEvent) -> Unit,
    audio: Map<String, String> = emptyMap(),
    setAudio: (Map<String, String>) -> Unit = {},
    prepareSpeech: (ReadArticle) -> Unit = {},
    settings: () -> xyz.cdr.builderlauncher.data.BuilderSettings = { xyz.cdr.builderlauncher.data.BuilderSettings() },
) {
    val article = articles.find { it.id == articleId }
    val downloads = rememberArticleDownloads(
        articles = articles,
        audio = audio,
        setAudio = setAudio,
        prepare = prepareSpeech,
        context = LocalContext.current,
        scope = rememberCoroutineScope(),
        settings = settings,
    )
    val playingThis = playback.articleId == article?.id
    val saved = article?.let { progress[it.id]?.positionMs } ?: 0L
    val pos = if (playingThis) playback.positionMs else saved
    val duration = article?.let { Reads.durationMs(it.text) } ?: 0L
    val chunk = if (playingThis && article != null) {
        val rows = Reads.chunks(article.text)
        rows.getOrNull(Reads.chunkIndexAt(rows, pos))
    } else {
        null
    }
    ReadOverlay(
        player = player,
        articles = articles,
        progress = progress,
        busy = busy,
        article = article,
        positionMs = pos,
        durationMs = duration,
        playing = playingThis && playback.playing,
        fraction = Reads.fraction(pos, duration),
        highlightStart = chunk?.start ?: -1,
        highlightEnd = chunk?.end ?: -1,
        speed = speed,
        prompt = prompt,
        input = input,
        hardware = hardware,
        onBackHome = { onEvent(ReadEvent.Home) },
        onBackList = { onEvent(ReadEvent.List) },
        onOpen = { onEvent(ReadEvent.Open(it.id)) },
        onToggle = {
            val row = article
            if (row != null) {
                PodcastPlayer.pause()
                speaker.toggle(row, saved, speed)
            }
        },
        onSkip = { delta ->
            article?.let { speaker.skip(it, delta, speed) }
        },
        onSeek = { frac ->
            article?.let { speaker.seek(it, Reads.progressAt(frac, duration), speed) }
        },
        onOpenBrowser = { article?.let { onEvent(ReadEvent.Browser(it.url)) } },
        onRemove = {
            val row = article
            if (row != null) {
                speaker.pause()
                reads.remove(row.id)
                onEvent(ReadEvent.List)
            }
        },
        onSpeed = { next ->
            reads.setSpeed(next)
            speaker.setSpeed(article, next)
        },
        onValue = { onEvent(ReadEvent.Value(it)) },
        onPick = { onEvent(ReadEvent.Pick(it)) },
        onClearMode = { onEvent(ReadEvent.Clear) },
        onSubmit = { onEvent(ReadEvent.Submit) },
        onSlash = { onEvent(ReadEvent.Slash(it)) },
        audio = audio,
        onDownload = { downloads.queue(it) },
        downloadLabel = { article ->
            val audioLabel = audio[article.id].orEmpty()
            if (!Reads.articleDownloadVisible(audioLabel)) {
                Reads.articleGenerationLabel(
                    audioLabel,
                    ArticleSpeechProgress.bytes[article.id] ?: 0L,
                    Reads.expectedSpeechBytes(Reads.durationMs(article.text)),
                )
            } else {
                downloads.label(article.id)
            }
        },
        downloadVisible = { Reads.articleDownloadVisible(audio[it.id].orEmpty()) },
        downloaded = { downloads.isDownloaded(it.id) },
    )
}

@Composable
fun ColumnScope.ReadOverlay(
    player: Boolean,
    articles: List<ReadArticle>,
    progress: Map<String, ReadProgress>,
    busy: Boolean,
    article: ReadArticle?,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    fraction: Float,
    highlightStart: Int,
    highlightEnd: Int,
    speed: Float,
    prompt: Char,
    input: String,
    hardware: Boolean,
    onBackHome: () -> Unit,
    onBackList: () -> Unit,
    onOpen: (ReadArticle) -> Unit,
    onToggle: () -> Unit,
    onSkip: (Long) -> Unit,
    onSeek: (Float) -> Unit,
    onOpenBrowser: () -> Unit,
    onRemove: () -> Unit,
    onSpeed: (Float) -> Unit,
    onValue: (String) -> Unit,
    onPick: (Char) -> Unit,
    onClearMode: () -> Unit,
    onSubmit: () -> Unit,
    onSlash: (SlashCommand) -> Unit,
    audio: Map<String, String> = emptyMap(),
    onDownload: (ReadArticle) -> Unit = {},
    downloadLabel: (ReadArticle) -> String = { "" },
    downloadVisible: (ReadArticle) -> Boolean = { false },
    downloaded: (ReadArticle) -> Boolean = { false },
) {
    var speedMenu by remember(article?.id) { mutableStateOf(false) }
    Column(Modifier.weight(1f)) {
        if (!player) {
            ReadListChrome(
                articles,
                progress,
                busy,
                onBackHome,
                onOpen,
                audio,
                onDownload,
                downloadLabel,
                downloadVisible,
                downloaded,
            )
        } else if (article == null) {
            ScreenBack(Reads.BACK, onBack = onBackList)
            Text("Article gone", color = Dim)
        } else {
            ReadPlayerChrome(
                publication = article.publication,
                title = article.title,
                position = Reads.positionLabel(positionMs, durationMs),
                playing = playing,
                progress = fraction,
                text = article.text,
                highlightStart = highlightStart,
                highlightEnd = highlightEnd,
                onBack = onBackList,
                onToggle = onToggle,
                onBack15 = { onSkip(-Reads.SKIP_MS) },
                onForward15 = { onSkip(Reads.SKIP_MS) },
                onSeek = onSeek,
                onOpen = onOpenBrowser,
                onRemove = onRemove,
                audioLabel = article.id.let { audio[it] }.orEmpty(),
                onDownload = { onDownload(article) },
                downloadLabel = downloadLabel(article),
                downloadVisible = downloadVisible(article),
                downloaded = downloaded(article),
                speed = {
                    PodcastSpeedMenu(
                        speed = speed,
                        expanded = speedMenu,
                        onExpanded = { speedMenu = it },
                        onPick = onSpeed,
                    )
                },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    CommandBar(
        prompt = prompt,
        value = input,
        hardware = hardware,
        onValue = onValue,
        onPick = onPick,
        onClearMode = onClearMode,
        onSubmit = onSubmit,
        onSlash = onSlash,
        onHub = onBackHome,
    )
}

@Composable
private fun ArticleDownloadMark(
    showButton: Boolean,
    downloaded: Boolean,
    label: String,
    onDownload: () -> Unit,
) {
    if (!showButton && label.isBlank()) return
    Column(horizontalAlignment = Alignment.End) {
        if (showButton) {
            DownloadIcon(
                filled = downloaded,
                modifier = Modifier
                    .semantics { contentDescription = if (downloaded) "downloaded" else "download" }
                    .clickable(onClick = onDownload)
                    .padding(start = 12.dp, top = 6.dp, bottom = 2.dp),
            )
        }
        if (label.isNotBlank()) {
            Text(
                label,
                color = Dim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun ReadSpeedLabel(speed: Float) {
    Text(
        Podcasts.formatSpeed(speed),
        color = Accent,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

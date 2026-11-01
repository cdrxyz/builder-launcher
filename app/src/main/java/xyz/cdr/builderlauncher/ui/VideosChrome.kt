package xyz.cdr.builderlauncher.ui

import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import xyz.cdr.builderlauncher.podcasts.PodcastPlaybackService
import xyz.cdr.builderlauncher.read.ArticlePlaybackService
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.videos.SavedVideo
import xyz.cdr.builderlauncher.videos.VideoPlayback
import xyz.cdr.builderlauncher.videos.VideoMedia
import xyz.cdr.builderlauncher.videos.VideoPlaybackService
import xyz.cdr.builderlauncher.videos.VideoPlayer
import xyz.cdr.builderlauncher.videos.Videos
import xyz.cdr.builderlauncher.videos.VideosRepository
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext

@Composable
fun SavedVideoCount(): Int {
    val repo = VideoMedia.videos ?: return 0
    val saved by repo.videos.collectAsState()
    return saved.size
}

@Composable
fun VideosHost(
    openId: String?,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val repo = VideoMedia.videos ?: return
    val player = VideoMedia.player ?: return
    val saved by repo.videos.collectAsState()
    val playback by player.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("<", color = Paper, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        Text(if (openId == null) Videos.TITLE else saved.find { it.id == openId }?.title ?: Videos.TITLE, color = Paper)
        if (openId != null) {
            val video = saved.find { it.id == openId }
            if (!playback.audioOnly && playback.videoId == openId) {
                AndroidView(
                    factory = { SurfaceView(it).also(player::attach) },
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(vertical = 8.dp),
                )
            }
            Text(
                if (playback.playing && playback.videoId == openId) "pause" else "play",
                color = Paper,
                modifier = Modifier.clickable {
                    if (video == null) return@clickable
                    if (playback.videoId == video.id && playback.playing) {
                        player.pause()
                    } else {
                        scope.launch { playSaved(ctx, repo, player, video, audioOnly = false) }
                    }
                }.padding(vertical = 8.dp),
            )
        } else {
            if (saved.isEmpty()) Text(Videos.EMPTY, color = Dim, modifier = Modifier.padding(vertical = 8.dp))
            saved.forEach { video ->
                Text(
                    video.title,
                    color = Paper,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(video.id) }.padding(vertical = 8.dp),
                )
                if (video.author.isNotBlank()) Text(video.author, color = Dim)
            }
        }
    }
}

fun submitSavedVideo(
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    line: String,
    onSaved: (String) -> Unit,
) {
    val videos = VideoMedia.videos ?: return
    val player = VideoMedia.player ?: return
    val raw = line.trim()
    if (raw.isEmpty()) return
    scope.launch {
        val picked = videos.resolve(raw) ?: run {
            Toast.makeText(context, Videos.FAILED, Toast.LENGTH_SHORT).show()
            return@launch
        }
        val saved = SavedVideo(
            id = picked.id,
            url = Videos.canonicalUrl(Videos.youtubeId(raw)),
            title = picked.title,
            author = picked.author,
            durationMs = picked.durationMs,
        )
        videos.remember(saved)
        onSaved(saved.id)
        playSaved(context, videos, player, saved, audioOnly = false)
    }
}

suspend fun playSaved(
    context: android.content.Context,
    videos: VideosRepository,
    player: VideoPlayer,
    video: SavedVideo,
    audioOnly: Boolean,
) {
    val picked = videos.resolve(video.url)
    if (picked == null || picked.audioUrl.isBlank()) {
        Toast.makeText(context, Videos.FAILED, Toast.LENGTH_SHORT).show()
        return
    }
    PodcastPlaybackService.stop(context)
    ArticlePlaybackService.stop(context)
    player.play(video, picked, audioOnly)
    VideoPlaybackService.start(context, video.title)
}

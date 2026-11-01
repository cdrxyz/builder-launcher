package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.podcasts.PodcastEpisode
import xyz.cdr.builderlauncher.read.ReadArticle
import xyz.cdr.builderlauncher.read.Reads
import xyz.cdr.builderlauncher.videos.SavedVideo

@Composable
fun PodcastIndexNowPlaying(
    video: SavedVideo?,
    videoPlaying: Boolean,
    showVideo: Boolean,
    article: ReadArticle?,
    showArticle: Boolean,
    articlePlaying: Boolean,
    episode: PodcastEpisode?,
    episodeShow: String,
    episodePlaying: Boolean,
    showEpisode: Boolean,
    onOpenVideo: () -> Unit,
    onToggleVideo: () -> Unit,
    onOpenArticle: () -> Unit,
    onToggleArticle: () -> Unit,
    onOpenEpisode: () -> Unit,
    onToggleEpisode: () -> Unit,
) {
    if (showVideo && video != null) {
        PodcastNowPlayingBar(video.title, "Saved Videos", videoPlaying, onOpen = onOpenVideo, onToggle = onToggleVideo)
        Spacer(Modifier.height(8.dp))
    } else if (showArticle && article != null) {
        PodcastNowPlayingBar(article.title, Reads.SAVED_ARTICLES, articlePlaying, onOpen = onOpenArticle, onToggle = onToggleArticle)
        Spacer(Modifier.height(8.dp))
    } else if (showEpisode && episode != null) {
        PodcastNowPlayingBar(episode.title, episodeShow, episodePlaying, onOpen = onOpenEpisode, onToggle = onToggleEpisode)
        Spacer(Modifier.height(8.dp))
    }
}

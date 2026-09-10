package xyz.cdr.builderlauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import xyz.cdr.builderlauncher.backup.BackupService
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.podcasts.PodcastPlayer
import xyz.cdr.builderlauncher.podcasts.PodcastsRepository
import xyz.cdr.builderlauncher.stocks.HomeTicker
import xyz.cdr.builderlauncher.stocks.Stocks
import xyz.cdr.builderlauncher.stocks.StocksRepository
import xyz.cdr.builderlauncher.weather.WeatherRefresh
import xyz.cdr.builderlauncher.weather.WeatherRepository

@Composable
internal fun BuilderLoops(
    lifecycleOwner: LifecycleOwner,
    settings: BuilderSettings,
    page: Page,
    watchSize: Int,
    playing: Boolean,
    episodeId: String?,
    weather: WeatherRepository,
    backup: BackupService,
    stocks: StocksRepository,
    podcasts: PodcastsRepository,
    tickerIndex: Int,
    onTickerIndex: (Int) -> Unit,
) {
    LaunchedEffect(lifecycleOwner, settings.weatherLat, settings.weatherLon) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            weather.refresh()
            while (true) {
                delay(WeatherRefresh.TTL_MS)
                weather.refresh()
            }
        }
    }
    LaunchedEffect(lifecycleOwner, settings.backupFrequency, settings.s3Endpoint, settings.s3Bucket) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            withContext(Dispatchers.IO) { backup.maybeUpload() }
        }
    }
    LaunchedEffect(lifecycleOwner, page, watchSize) {
        val needQuotes = (page == Page.Home && watchSize > 0) ||
            page == Page.Stocks || page == Page.StockDetail
        if (!needQuotes) return@LaunchedEffect
        val interval = Stocks.quoteIntervalMs(page == Page.Home)
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            stocks.refreshQuotes()
            while (true) {
                delay(interval)
                stocks.refreshQuotes()
            }
        }
    }
    LaunchedEffect(lifecycleOwner, page, watchSize) {
        if (watchSize <= 0 || page != Page.Home) {
            if (watchSize <= 0) onTickerIndex(0)
            return@LaunchedEffect
        }
        var index = tickerIndex.mod(watchSize)
        onTickerIndex(index)
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(HomeTicker.ROTATE_MS)
                index = HomeTicker.nextIndex(watchSize, index)
                onTickerIndex(index)
            }
        }
    }
    LaunchedEffect(page) {
        if (page == Page.Podcasts || page == Page.PodcastShow) {
            podcasts.refreshAll()
        }
    }
    LaunchedEffect(lifecycleOwner, playing, episodeId) {
        if (!playing) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(500)
                PodcastPlayer.poll()
            }
        }
    }
}

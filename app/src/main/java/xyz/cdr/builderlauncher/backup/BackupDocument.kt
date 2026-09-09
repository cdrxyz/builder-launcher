package xyz.cdr.builderlauncher.backup

import kotlinx.serialization.Serializable
import xyz.cdr.builderlauncher.clock.ClockAlarm
import xyz.cdr.builderlauncher.clock.WorldClock
import xyz.cdr.builderlauncher.data.AppIcons
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.ChatThread
import xyz.cdr.builderlauncher.data.ClockFace
import xyz.cdr.builderlauncher.data.KeyboardMode
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.LocalItem
import xyz.cdr.builderlauncher.data.StockInsert
import xyz.cdr.builderlauncher.data.WeatherUnits
import xyz.cdr.builderlauncher.clock.ClockSound
import xyz.cdr.builderlauncher.podcasts.EpisodeProgress
import xyz.cdr.builderlauncher.podcasts.PodcastEpisode
import xyz.cdr.builderlauncher.podcasts.PodcastShow
import xyz.cdr.builderlauncher.stocks.WatchItem

@Serializable
data class BackupDocument(
    val version: Int = 1,
    val exportedAt: Long,
    val items: List<LocalItem> = emptyList(),
    val chats: List<ChatThread> = emptyList(),
    val pins: List<String> = emptyList(),
    val watchlist: List<WatchItem> = emptyList(),
    val podcasts: PodcastBackup = PodcastBackup(),
    val alarms: List<ClockAlarm> = emptyList(),
    val zones: List<WorldClock> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
)

@Serializable
data class PodcastBackup(
    val shows: List<PodcastShow> = emptyList(),
    val episodes: List<PodcastEpisode> = emptyList(),
    val progress: List<EpisodeProgress> = emptyList(),
    val cacheBytes: Long = 0L,
)

@Serializable
data class BackupSettings(
    val provider: LlmProvider = LlmProvider.HERMES,
    val hermesBaseUrl: String = "",
    val hermesOpenInHermex: Boolean = false,
    val hermesWebUrl: String = "",
    val apiKey: String? = null,
    val model: String = "",
    val keyboardMode: KeyboardMode = KeyboardMode.AUTO,
    val weatherPlace: String = "",
    val weatherLat: Double? = null,
    val weatherLon: Double? = null,
    val weatherUnits: WeatherUnits = WeatherUnits.METRIC,
    val accentHex: String = "",
    val stockInsert: StockInsert = StockInsert.TOP,
    val clockSound: ClockSound = ClockSound.PULSE,
    val appIcons: AppIcons = AppIcons.PLAINTEXT,
    val clockFace: ClockFace = ClockFace.ANALOG,
) {
    companion object {
        fun from(settings: BuilderSettings, includeApiKey: Boolean): BackupSettings = BackupSettings(
            provider = settings.provider,
            hermesBaseUrl = settings.hermesBaseUrl,
            hermesOpenInHermex = settings.hermesOpenInHermex,
            hermesWebUrl = settings.hermesWebUrl,
            apiKey = if (includeApiKey) settings.apiKey else null,
            model = settings.model,
            keyboardMode = settings.keyboardMode,
            weatherPlace = settings.weatherPlace,
            weatherLat = settings.weatherLat,
            weatherLon = settings.weatherLon,
            weatherUnits = settings.weatherUnits,
            accentHex = settings.accentHex,
            stockInsert = settings.stockInsert,
            clockSound = settings.clockSound,
            appIcons = settings.appIcons,
            clockFace = settings.clockFace,
        )
    }
}

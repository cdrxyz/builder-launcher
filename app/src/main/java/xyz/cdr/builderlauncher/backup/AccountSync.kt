package xyz.cdr.builderlauncher.backup

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.cdr.builderlauncher.clock.ClockStore
import xyz.cdr.builderlauncher.data.ChatStore
import xyz.cdr.builderlauncher.data.HabitStore
import xyz.cdr.builderlauncher.data.LocalLists
import xyz.cdr.builderlauncher.data.PinnedApps
import xyz.cdr.builderlauncher.data.SettingsRepository
import xyz.cdr.builderlauncher.podcasts.PodcastPlayer
import xyz.cdr.builderlauncher.podcasts.PodcastsRepository
import xyz.cdr.builderlauncher.read.ArticlePlayer
import xyz.cdr.builderlauncher.read.ReadsRepository
import xyz.cdr.builderlauncher.stocks.StocksRepository
import xyz.cdr.builderlauncher.videos.VideosRepository

/**
 * Playback and the media services outlive the screen. The stores live once per
 * process, so a pulse works even when the launcher was never opened in this
 * process — a sticky service can restart alone after the system kills us.
 */
object AccountSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    class Stores(
        val settings: SettingsRepository,
        val lists: LocalLists,
        val chats: ChatStore,
        val pins: PinnedApps,
        val stocks: StocksRepository,
        val podcasts: PodcastsRepository,
        val reads: ReadsRepository,
        val videos: VideosRepository,
        val habits: HabitStore,
        val backup: BackupService,
    ) {
        /** The activity installs its article player so a pulse persists its position too. */
        @Volatile var articlePlayer: ArticlePlayer? = null
    }

    @Volatile private var cached: Stores? = null

    fun stores(context: Context): Stores = cached ?: synchronized(this) {
        cached ?: build(context.applicationContext).also { cached = it }
    }

    private fun build(app: Context): Stores {
        val settings = SettingsRepository(app)
        val lists = LocalLists(app)
        val chats = ChatStore(app)
        val pins = PinnedApps(app)
        val stocks = StocksRepository(app)
        val podcasts = PodcastsRepository(app)
        val reads = ReadsRepository(app)
        val videos = VideosRepository(app)
        val habits = HabitStore(app)
        val backup = BackupService(
            app,
            settings,
            lists,
            chats,
            pins,
            stocks,
            podcasts,
            reads,
            videos,
            ClockStore.get(app),
            habits,
        )
        return Stores(settings, lists, chats, pins, stocks, podcasts, reads, videos, habits, backup)
    }

    @Volatile
    var upload: (suspend () -> Unit)? = null

    fun pulse() {
        val run = upload ?: return
        scope.launch { run() }
    }

    /** Idempotent; the activity calls it and so does every playback service's onCreate. */
    fun install(context: Context) {
        val s = stores(context)
        if (upload != null) return
        upload = {
            withContext(Dispatchers.Main) {
                PodcastPlayer.persist()
                s.articlePlayer?.persist()
            }
            s.backup.markLocalChange()
            s.backup.tickAuto()
        }
    }
}

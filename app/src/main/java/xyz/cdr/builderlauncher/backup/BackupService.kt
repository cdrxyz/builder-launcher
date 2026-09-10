package xyz.cdr.builderlauncher.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.cdr.builderlauncher.clock.ClockScheduler
import xyz.cdr.builderlauncher.clock.ClockSnapshot
import xyz.cdr.builderlauncher.clock.ClockStore
import xyz.cdr.builderlauncher.clock.TimerState
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.ChatStore
import xyz.cdr.builderlauncher.data.LocalLists
import xyz.cdr.builderlauncher.data.PinnedApps
import xyz.cdr.builderlauncher.data.SettingsRepository
import xyz.cdr.builderlauncher.podcasts.PodcastsRepository
import xyz.cdr.builderlauncher.stocks.StocksRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupService(
    private val context: Context,
    private val settings: SettingsRepository,
    private val lists: LocalLists,
    private val chats: ChatStore,
    private val pins: PinnedApps,
    private val stocks: StocksRepository,
    private val podcasts: PodcastsRepository,
    private val clock: ClockStore,
    private val s3: S3Client = S3Client(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    },
) {
    fun document(nowMs: Long = System.currentTimeMillis()): BackupDocument {
        val snap = clock.snapshot()
        val current = settings.settings.value
        return BackupDocument(
            exportedAt = nowMs,
            items = lists.items.value,
            chats = chats.threads.value,
            pins = pins.packages.value,
            watchlist = stocks.watch.value,
            podcasts = podcasts.exportBackup(),
            alarms = snap.alarms,
            zones = snap.zones,
            settings = BackupSettings.from(current, includeApiKey = current.backupIncludeAiCredentials),
        )
    }

    fun encode(document: BackupDocument): String = json.encodeToString(document)

    fun decode(text: String): BackupDocument = json.decodeFromString(text)

    fun upload(nowMs: Long = System.currentTimeMillis()): String {
        val s = settings.settings.value
        requireReady(s)
        val plain = encode(document(nowMs = nowMs)).toByteArray(Charsets.UTF_8)
        val blob = BackupCrypto.encrypt(plain, s.s3EncryptionKey)
        s3.put(s.s3Endpoint, s.s3Bucket, s.s3AccessKey, s.s3SecretKey, blob, nowMs)
        settings.markBackup(nowMs)
        return "Uploaded encrypted backup"
    }

    fun restore(): String {
        val s = settings.settings.value
        requireReady(s)
        val blob = s3.get(s.s3Endpoint, s.s3Bucket, s.s3AccessKey, s.s3SecretKey)
        val plain = BackupCrypto.decrypt(blob, s.s3EncryptionKey)
        val doc = decode(plain.decodeToString())
        apply(doc)
        return "Restored ${summary(doc)}"
    }

    fun apply(doc: BackupDocument) {
        lists.replaceAll(doc.items)
        chats.replaceAll(doc.chats)
        pins.replaceAll(doc.pins)
        stocks.replaceAll(doc.watchlist)
        podcasts.importBackup(doc.podcasts)
        clock.replaceFromBackup(
            ClockSnapshot(
                timer = TimerState(),
                alarms = doc.alarms,
                zones = doc.zones,
                alert = null,
            ),
        )
        ClockScheduler.reconcile(context, clock)
        settings.applyBackup(doc.settings)
    }

    fun shareUnencrypted() {
        val text = encode(document())
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        val file = File(dir, "builder-launcher.json")
        file.writeText(text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Builder Launcher backup")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(
            Intent.createChooser(send, "Share backup")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun maybeUpload(nowMs: Long = System.currentTimeMillis()): String? {
        val s = settings.settings.value
        if (!S3Signer.ready(s.s3Endpoint, s.s3Bucket, s.s3AccessKey, s.s3SecretKey, s.s3EncryptionKey)) {
            return null
        }
        if (!s.backupFrequency.due(nowMs, s.lastBackupAtEpochMs)) return null
        return runCatching { upload(nowMs) }.getOrElse { it.message }
    }

    fun probe(): S3Access.Done {
        val s = settings.settings.value
        if (!S3Signer.credentialsReady(s.s3Endpoint, s.s3Bucket, s.s3AccessKey, s.s3SecretKey)) {
            return S3AccessReport.missingFields()
        }
        return s3.probe(s.s3Endpoint, s.s3Bucket, s.s3AccessKey, s.s3SecretKey, s.s3EncryptionKey)
    }

    companion object {
        fun lastBackupLabel(epochMs: Long): String {
            if (epochMs <= 0L) return "never"
            val fmt = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
            return fmt.format(Date(epochMs))
        }

        private fun requireReady(s: BuilderSettings) {
            if (!S3Signer.ready(s.s3Endpoint, s.s3Bucket, s.s3AccessKey, s.s3SecretKey, s.s3EncryptionKey)) {
                throw IllegalStateException("S3 endpoint, bucket, keys, and encryption key are required")
            }
        }

        private fun summary(doc: BackupDocument): String {
            val parts = mutableListOf<String>()
            if (doc.items.isNotEmpty()) parts += "${doc.items.size} items"
            if (doc.chats.isNotEmpty()) parts += "${doc.chats.size} chats"
            if (doc.pins.isNotEmpty()) parts += "${doc.pins.size} pins"
            if (doc.watchlist.isNotEmpty()) parts += "${doc.watchlist.size} tickers"
            if (doc.podcasts.shows.isNotEmpty()) parts += "${doc.podcasts.shows.size} podcasts"
            if (doc.alarms.isNotEmpty()) parts += "${doc.alarms.size} alarms"
            if (parts.isEmpty()) return "settings"
            return parts.joinToString(", ")
        }
    }
}

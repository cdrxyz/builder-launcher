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
    private val account: AccountClient = AccountClient(),
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
        if (s.accountToken.isNotBlank()) return syncUp(nowMs)
        requireReady(s)
        val plain = encode(document(nowMs = nowMs)).toByteArray(Charsets.UTF_8)
        val blob = BackupCrypto.encrypt(plain, s.s3EncryptionKey)
        s3.put(s.s3Endpoint, s.s3Bucket, s.s3AccessKey, s.s3SecretKey, blob, nowMs)
        settings.markBackup(nowMs)
        return "Uploaded encrypted backup"
    }

    fun restore(): String {
        val s = settings.settings.value
        if (s.accountToken.isNotBlank()) return syncDown()
        requireReady(s)
        val blob = s3.get(s.s3Endpoint, s.s3Bucket, s.s3AccessKey, s.s3SecretKey)
        val plain = BackupCrypto.decrypt(blob, s.s3EncryptionKey)
        val remote = decode(plain.decodeToString())
        apply(remote)
        return "Restored ${summary(remote)}"
    }

    fun signup(email: String, password: String): String {
        val session = account.signup(email, password)
        settings.update { it.copy(accountEmail = session.email, accountToken = session.token) }
        return "Created ${session.email}"
    }

    fun login(email: String, password: String): String {
        val session = account.login(email, password)
        settings.update { it.copy(accountEmail = session.email, accountToken = session.token) }
        return "Signed in as ${session.email}"
    }

    fun logout(): String {
        val token = settings.settings.value.accountToken
        if (token.isNotBlank()) account.logout(token)
        settings.update { it.copy(accountEmail = "", accountToken = "") }
        return "Signed out"
    }

    private fun syncUp(nowMs: Long): String {
        val s = settings.settings.value
        val local = document(nowMs = nowMs)
        val remote = account.getVault(s.accountToken)
        val merged = BackupMerge.join(local, remote.document).copy(exportedAt = nowMs)
        account.putVault(s.accountToken, merged)
        apply(merged)
        settings.markBackup(nowMs)
        return "Synced to builder.cdr.xyz"
    }

    private fun syncDown(): String {
        val s = settings.settings.value
        val remote = account.getVault(s.accountToken)
        val remoteDoc = remote.document ?: return "No cloud snapshot yet. Sync now to upload this phone."
        apply(remoteDoc)
        settings.markBackup(System.currentTimeMillis())
        return "Restored ${summary(remoteDoc)}"
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
        if (!s.backupFrequency.due(nowMs, s.lastBackupAtEpochMs)) return null
        if (s.accountToken.isNotBlank()) {
            return runCatching { upload(nowMs) }.getOrElse { it.message }
        }
        if (!S3Signer.ready(s.s3Endpoint, s.s3Bucket, s.s3AccessKey, s.s3SecretKey, s.s3EncryptionKey)) {
            return null
        }
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
        const val OVERWRITE_WARNING =
            "This will overwrite any local data on this device (todos, notes, chats, pins, stocks, podcasts, alarms, and settings). OAuth tokens stay here. Continue?"

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

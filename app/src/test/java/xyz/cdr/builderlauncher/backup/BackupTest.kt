package xyz.cdr.builderlauncher.backup

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.clock.ClockAlarm
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.LocalItem
import xyz.cdr.builderlauncher.podcasts.EpisodeProgress
import xyz.cdr.builderlauncher.podcasts.PodcastEpisode
import xyz.cdr.builderlauncher.podcasts.PodcastShow
import xyz.cdr.builderlauncher.stocks.WatchItem
import java.security.SecureRandom

class BackupCryptoTest {
    @Test
    fun roundTrip() {
        val plain = "hello builder".toByteArray()
        val blob = BackupCrypto.encrypt(plain, "passphrase", SecureRandom())
        assertArrayEquals(plain, BackupCrypto.decrypt(blob, "passphrase"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongKeyFails() {
        val blob = BackupCrypto.encrypt("secret".toByteArray(), "one", SecureRandom())
        BackupCrypto.decrypt(blob, "two")
    }
}

class BackupFrequencyTest {
    @Test
    fun offNeverDue() {
        assertFalse(BackupFrequency.OFF.due(1_000, 0))
        assertFalse(BackupFrequency.OFF.due(1_000, 10))
    }

    @Test
    fun autoIsNotAScheduledDump() {
        assertFalse(BackupFrequency.AUTO.due(1_000, 0))
        assertFalse(BackupFrequency.AUTO.due(1_000, 10))
    }

    @Test
    fun parseFallsBackToAuto() {
        assertEquals(BackupFrequency.AUTO, BackupFrequency.parse(null))
        assertEquals(BackupFrequency.AUTO, BackupFrequency.parse("nope"))
        assertEquals(BackupFrequency.OFF, BackupFrequency.parse("off"))
    }

    @Test
    fun dailyDueWhenNeverBackedUp() {
        assertTrue(BackupFrequency.DAILY.due(nowMs = 10, lastBackupAtEpochMs = 0))
    }

    @Test
    fun dailyNotDueInsideWindow() {
        val last = 1_000L
        assertFalse(BackupFrequency.DAILY.due(last + 60_000, last))
        assertTrue(BackupFrequency.DAILY.due(last + 24L * 60 * 60 * 1000, last))
    }

    @Test
    fun weeklyDueAfterSevenDays() {
        val last = 1_000L
        assertFalse(BackupFrequency.WEEKLY.due(last + 6L * 24 * 60 * 60 * 1000, last))
        assertTrue(BackupFrequency.WEEKLY.due(last + 7L * 24 * 60 * 60 * 1000, last))
    }
}

class BackupAutoTest {
    @Test
    fun idleWhenNotReadyOrNotAuto() {
        assertEquals(
            BackupAuto.Action.NONE,
            BackupAuto.action(
                BackupFrequency.AUTO,
                dirty = true,
                lastBackupAtEpochMs = 0,
                lastPullAtEpochMs = 0,
                nowMs = 10,
                ready = false,
            ),
        )
        assertEquals(
            BackupAuto.Action.NONE,
            BackupAuto.action(
                BackupFrequency.DAILY,
                dirty = true,
                lastBackupAtEpochMs = 0,
                lastPullAtEpochMs = 0,
                nowMs = 10,
                ready = true,
            ),
        )
    }

    @Test
    fun writePushesBeforePull() {
        assertEquals(
            BackupAuto.Action.PUSH,
            BackupAuto.action(
                BackupFrequency.AUTO,
                dirty = true,
                lastBackupAtEpochMs = 5,
                lastPullAtEpochMs = 0,
                nowMs = 10,
                ready = true,
            ),
        )
    }

    @Test
    fun pullWhenIntervalElapsed() {
        val last = 1_000L
        assertEquals(
            BackupAuto.Action.NONE,
            BackupAuto.action(
                BackupFrequency.AUTO,
                dirty = false,
                lastBackupAtEpochMs = last,
                lastPullAtEpochMs = last,
                nowMs = last + 60_000,
                ready = true,
            ),
        )
        assertEquals(
            BackupAuto.Action.PULL,
            BackupAuto.action(
                BackupFrequency.AUTO,
                dirty = false,
                lastBackupAtEpochMs = last,
                lastPullAtEpochMs = last,
                nowMs = last + BackupFrequency.PULL_INTERVAL_MS,
                ready = true,
            ),
        )
    }

    @Test
    fun firstOpenPulls() {
        assertEquals(
            BackupAuto.Action.PULL,
            BackupAuto.action(
                BackupFrequency.AUTO,
                dirty = false,
                lastBackupAtEpochMs = 0,
                lastPullAtEpochMs = 0,
                nowMs = 10,
                ready = true,
            ),
        )
    }
}

class BackupDocumentTest {
    @Test
    fun includeOffOmitsApiKey() {
        val settings = BackupSettings.from(
            xyz.cdr.builderlauncher.data.BuilderSettings(
                provider = LlmProvider.XAI,
                apiKey = "sk-secret",
            ),
            includeApiKey = false,
        )
        assertEquals(null, settings.apiKey)
        val encoded = kotlinx.serialization.json.Json.encodeToString(
            BackupDocument.serializer(),
            BackupDocument(
                exportedAt = 1,
                items = listOf(LocalItem("1", "todo", "buy milk", 1)),
                settings = settings,
            ),
        )
        assertFalse(encoded.contains("sk-secret"))
        assertTrue(encoded.contains("buy milk"))
    }

    @Test
    fun includeOnCarriesApiKey() {
        val settings = BackupSettings.from(
            xyz.cdr.builderlauncher.data.BuilderSettings(apiKey = "sk-secret"),
            includeApiKey = true,
        )
        assertEquals("sk-secret", settings.apiKey)
    }

    @Test
    fun includeOffOmitsOAuth() {
        val settings = BackupSettings.from(
            xyz.cdr.builderlauncher.data.BuilderSettings(
                provider = LlmProvider.XAI,
                oauthAccess = "access-secret",
                oauthRefresh = "refresh-secret",
                oauthAccount = "me@example.com",
            ),
            includeApiKey = false,
        )
        assertEquals(null, settings.oauthAccess)
        assertEquals(null, settings.oauthRefresh)
        val encoded = kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(
            BackupDocument.serializer(),
            BackupDocument(exportedAt = 1, settings = settings),
        )
        assertFalse(encoded.contains("access-secret"))
        assertFalse(encoded.contains("refresh-secret"))
    }

    @Test
    fun includeOnCarriesOAuth() {
        val settings = BackupSettings.from(
            xyz.cdr.builderlauncher.data.BuilderSettings(
                provider = LlmProvider.XAI,
                oauthAccess = "access-secret",
                oauthRefresh = "refresh-secret",
                oauthExpiresAtEpochMs = 50L,
                oauthAccount = "me@example.com",
            ),
            includeApiKey = true,
        )
        assertEquals("access-secret", settings.oauthAccess)
        assertEquals("refresh-secret", settings.oauthRefresh)
        assertEquals(50L, settings.oauthExpiresAtEpochMs)
        assertEquals("me@example.com", settings.oauthAccount)
    }

    @Test
    fun backupCarriesHomeTodoCount() {
        val settings = BackupSettings.from(
            xyz.cdr.builderlauncher.data.BuilderSettings(homeTodoCount = 5),
            includeApiKey = false,
        )
        assertEquals(5, settings.homeTodoCount)
    }

    @Test
    fun slimForAccountKeepsSubscriptionsAndPlayedProgressOnly() {
        val html = "d".repeat(100_000)
        val fat = BackupDocument(
            exportedAt = 1,
            watchlist = listOf(
                WatchItem("AAPL", "Apple", addedAt = 1, price = 190.0, changePercent = 1.2, previousClose = 188.0),
            ),
            podcasts = PodcastBackup(
                shows = listOf(PodcastShow("https://feeds.example/show", "Show")),
                episodes = (1..50).map { i ->
                    PodcastEpisode(
                        id = "ep-$i",
                        showId = "https://feeds.example/show",
                        title = "Ep $i",
                        enclosureUrl = "https://cdn.example/$i.mp3",
                        description = html,
                    )
                },
                progress = listOf(
                    EpisodeProgress("ep-1", positionMs = 12_000, lastPlayedAt = 9),
                    EpisodeProgress("ep-2", positionMs = 0, lastPlayedAt = 0),
                ),
                cacheBytes = 99,
            ),
        )
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val full = json.encodeToString(BackupDocument.serializer(), fat)
        val slimDoc = fat.slimForAccount()
        val slim = json.encodeToString(BackupDocument.serializer(), slimDoc)
        assertTrue(full.length > 4_000_000)
        assertTrue(slim.length < 2_000)
        assertTrue(slimDoc.podcasts.episodes.isEmpty())
        assertEquals(listOf("ep-1"), slimDoc.podcasts.progress.map { it.episodeId })
        assertEquals("Show", slimDoc.podcasts.shows.single().title)
        assertEquals("AAPL", slimDoc.watchlist.single().symbol)
        assertEquals(null, slimDoc.watchlist.single().price)
        assertEquals(0L, slimDoc.podcasts.cacheBytes)
    }
}

class S3SignerTest {
    @Test
    fun r2RegionIsAuto() {
        assertEquals("auto", S3Signer.regionFor("https://abc.r2.cloudflarestorage.com"))
    }

    @Test
    fun awsRegionalEndpoint() {
        assertEquals("us-west-2", S3Signer.regionFor("https://s3.us-west-2.amazonaws.com"))
    }

    @Test
    fun objectUrlIsPathStyle() {
        assertEquals(
            "https://abc.r2.cloudflarestorage.com/my-bucket/builder-launcher/backup.enc",
            S3Signer.objectUrl("https://abc.r2.cloudflarestorage.com/", "my-bucket"),
        )
    }

    @Test
    fun credentialsReadyDoesNotNeedEncryptionKey() {
        assertTrue(S3Signer.credentialsReady("https://x", "b", "a", "s"))
        assertFalse(S3Signer.credentialsReady("https://x", "b", "a", ""))
        assertFalse(S3Signer.ready("https://x", "b", "a", "s", ""))
    }

    @Test
    fun signIsStableForFixedInputs() {
        val headers = S3Signer.sign(
            method = "PUT",
            url = "https://s3.us-east-1.amazonaws.com/bucket/builder-launcher/backup.enc",
            accessKey = "AKIAIOSFODNN7EXAMPLE",
            secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
            payload = "hello".toByteArray(),
            amzDate = "20130524T000000Z",
            extraHeaders = mapOf("content-type" to "application/octet-stream"),
        )
        val auth = headers.getValue("authorization")
        assertTrue(auth.startsWith("AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request"))
        assertTrue(auth.contains("Signature="))
        assertEquals(64, auth.substringAfter("Signature=").length)
        val again = S3Signer.sign(
            method = "PUT",
            url = "https://s3.us-east-1.amazonaws.com/bucket/builder-launcher/backup.enc",
            accessKey = "AKIAIOSFODNN7EXAMPLE",
            secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
            payload = "hello".toByteArray(),
            amzDate = "20130524T000000Z",
            extraHeaders = mapOf("content-type" to "application/octet-stream"),
        )
        assertEquals(auth, again.getValue("authorization"))
    }
}

class S3ClientTest {
    @Test
    fun putSignsAndHitsPathStyleUrl() {
        var captured: okhttp3.Request? = null
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            captured = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ByteArray(0).toResponseBody())
                .build()
        }.build()
        S3Client(http).put(
            endpoint = "https://abc.r2.cloudflarestorage.com",
            bucket = "my-bucket",
            accessKey = "AKI",
            secretKey = "SECRET",
            body = "payload".toByteArray(),
            nowMs = 1_368_953_040_000L,
        )
        val req = captured!!
        assertEquals("PUT", req.method)
        assertEquals(
            "https://abc.r2.cloudflarestorage.com/my-bucket/builder-launcher/backup.enc",
            req.url.toString(),
        )
        val auth = req.header("Authorization")!!
        assertTrue(auth.startsWith("AWS4-HMAC-SHA256 Credential=AKI/"))
        assertTrue(auth.contains("/auto/s3/aws4_request"))
    }

    @Test
    fun get404IsReadable() {
        val http = OkHttpClient.Builder().addInterceptor {
            Response.Builder()
                .request(it.request())
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body("missing".toResponseBody())
                .build()
        }.build()
        try {
            S3Client(http).get("https://abc.r2.cloudflarestorage.com", "b", "a", "s")
            throw AssertionError("expected 404")
        } catch (e: IllegalStateException) {
            assertEquals("No backup in that bucket", e.message)
        }
    }

    @Test
    fun probeTreats404AsReachable() {
        val http = OkHttpClient.Builder().addInterceptor {
            Response.Builder()
                .request(it.request())
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body(ByteArray(0).toResponseBody())
                .build()
        }.build()
        val result = S3Client(http).probe("https://abc.r2.cloudflarestorage.com", "b", "a", "s")
        assertTrue(result.ok)
        assertEquals("S3 access good — no backup yet", result.line)
    }

    @Test
    fun probeTreats403AsDenied() {
        val http = OkHttpClient.Builder().addInterceptor {
            Response.Builder()
                .request(it.request())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .body(ByteArray(0).toResponseBody())
                .build()
        }.build()
        val result = S3Client(http).probe("https://abc.r2.cloudflarestorage.com", "b", "a", "s")
        assertFalse(result.ok)
        assertEquals("S3 access denied", result.line)
    }

    @Test
    fun probeDecryptsPresentBackup() {
        val blob = BackupCrypto.encrypt("payload".toByteArray(), "pass", java.security.SecureRandom())
        val http = OkHttpClient.Builder().addInterceptor {
            Response.Builder()
                .request(it.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(blob.toResponseBody())
                .build()
        }.build()
        val ok = S3Client(http).probe("https://abc.r2.cloudflarestorage.com", "b", "a", "s", "pass")
        assertTrue(ok.ok)
        assertEquals("S3 access good — backup decrypts", ok.line)
        val bad = S3Client(http).probe("https://abc.r2.cloudflarestorage.com", "b", "a", "s", "nope")
        assertFalse(bad.ok)
        assertEquals("Wrong encryption key", bad.line)
    }

    @Test
    fun probeAsksForEncryptionKeyWhenBackupPresent() {
        val http = OkHttpClient.Builder().addInterceptor {
            Response.Builder()
                .request(it.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("x".toByteArray().toResponseBody())
                .build()
        }.build()
        val result = S3Client(http).probe("https://abc.r2.cloudflarestorage.com", "b", "a", "s", "")
        assertTrue(result.ok)
        assertEquals("S3 access good — backup present. Set encryption key to verify.", result.line)
    }
}

class BackupMergeTest {
    @Test
    fun unionsItemsAndDropsDeleted() {
        val a = BackupDocument(
            exportedAt = 2,
            items = listOf(LocalItem("1", "todo", "new", createdAt = 1, updatedAt = 5)),
            deletedIds = listOf("9"),
        )
        val b = BackupDocument(
            exportedAt = 1,
            items = listOf(
                LocalItem("1", "todo", "old", createdAt = 1, updatedAt = 1),
                LocalItem("2", "todo", "keep", createdAt = 1),
                LocalItem("9", "todo", "gone", createdAt = 1),
            ),
        )
        val merged = BackupMerge.merge(a, b)
        assertEquals("new", merged.items.find { it.id == "1" }?.text)
        assertTrue(merged.items.any { it.id == "2" })
        assertFalse(merged.items.any { it.id == "9" })
    }

    @Test
    fun firstJoinKeepsLocalWhenRemoteIsEmpty() {
        val local = BackupDocument(
            exportedAt = 10,
            items = listOf(
                LocalItem("todo-1", "todo", "buy milk", createdAt = 1, updatedAt = 1),
                LocalItem("note-1", "note", "secret", createdAt = 2, updatedAt = 2),
            ),
            pins = listOf("xyz.cdr.builderlauncher"),
            watchlist = listOf(WatchItem("AAPL", "Apple", addedAt = 1)),
            podcasts = PodcastBackup(
                shows = listOf(PodcastShow("https://feeds.example/show", "Show")),
                episodes = listOf(PodcastEpisode("ep-1", "https://feeds.example/show", "Ep")),
            ),
        )
        val emptyRemote = BackupDocument(exportedAt = 1)
        val fromNull = BackupMerge.join(local, null)
        val fromEmpty = BackupMerge.join(local, emptyRemote)
        for (joined in listOf(fromNull, fromEmpty)) {
            assertEquals("buy milk", joined.items.find { it.id == "todo-1" }?.text)
            assertEquals("secret", joined.items.find { it.id == "note-1" }?.text)
            assertTrue(joined.pins.contains("xyz.cdr.builderlauncher"))
            assertEquals("AAPL", joined.watchlist.single().symbol)
            assertEquals("Show", joined.podcasts.shows.single().title)
            assertEquals("ep-1", joined.podcasts.episodes.single().id)
        }
    }

    @Test
    fun firstJoinUnionsLocalWithExistingAccountOrS3() {
        val local = BackupDocument(
            exportedAt = 5,
            items = listOf(LocalItem("phone", "todo", "on phone", createdAt = 1, updatedAt = 1)),
            watchlist = listOf(WatchItem("TSLA", "Tesla", addedAt = 1)),
        )
        val remote = BackupDocument(
            exportedAt = 8,
            items = listOf(LocalItem("cloud", "note", "in cloud", createdAt = 2, updatedAt = 2)),
            watchlist = listOf(WatchItem("AAPL", "Apple", addedAt = 2)),
        )
        val joined = BackupMerge.join(local, remote)
        assertTrue(joined.items.any { it.id == "phone" && it.text == "on phone" })
        assertTrue(joined.items.any { it.id == "cloud" && it.text == "in cloud" })
        assertEquals(setOf("AAPL", "TSLA"), joined.watchlist.map { it.symbol }.toSet())
    }

    @Test
    fun mergePutsUnseenTasksOnTopAndKeepsANewerTextEdit() {
        val phone = BackupDocument(
            exportedAt = 20,
            items = listOf(
                LocalItem("a", "todo", "alpha", createdAt = 100, order = 599, orderedAt = 600),
                LocalItem("b", "todo", "beta", createdAt = 200, order = 598, orderedAt = 600),
                LocalItem("note", "note", "keep", createdAt = 5),
            ),
        )
        val web = BackupDocument(
            exportedAt = 10,
            items = listOf(
                LocalItem("a", "todo", "alpha edited", createdAt = 100, updatedAt = 700),
                LocalItem("b", "todo", "beta", createdAt = 200),
                LocalItem("c", "todo", "from web", createdAt = 400),
                LocalItem("note", "note", "keep", createdAt = 5),
            ),
        )
        val merged = BackupMerge.merge(phone, web)
        assertEquals(
            listOf("from web", "alpha edited", "beta", "keep"),
            merged.items.map { it.text },
        )
        assertEquals(700L, merged.items.first { it.id == "a" }.updatedAt)
        assertEquals(599L, merged.items.first { it.id == "a" }.order)
        assertEquals(600L, merged.items.first { it.id == "a" }.orderedAt)
    }

    @Test
    fun mergeKeepsLocalShowNotesWhenCloudOmitsThem() {
        val html = "<p>show notes</p>"
        val local = BackupDocument(
            exportedAt = 10,
            podcasts = PodcastBackup(
                episodes = listOf(
                    PodcastEpisode("ep-1", "https://feeds.example/show", "Ep", pubDate = 5, description = html),
                ),
            ),
        )
        val remote = BackupDocument(
            exportedAt = 11,
            podcasts = PodcastBackup(
                episodes = listOf(
                    PodcastEpisode("ep-1", "https://feeds.example/show", "Ep", pubDate = 5, description = ""),
                ),
            ),
        )
        val joined = BackupMerge.join(local, remote)
        assertEquals(html, joined.podcasts.episodes.single().description)
    }

    @Test
    fun mergeDropsAnAlarmDeletedOnEitherSide() {
        val gone = ClockAlarm(id = "a-gone", hour = 7, minute = 30, label = "up")
        val keep = ClockAlarm(id = "a-keep", hour = 8, minute = 0, label = "keep")
        val phone = BackupDocument(
            exportedAt = 20,
            alarms = listOf(keep),
            deletedAlarmIds = listOf(gone.id),
        )
        val cloud = BackupDocument(
            exportedAt = 10,
            alarms = listOf(gone, keep),
        )
        val merged = BackupMerge.merge(phone, cloud)
        assertEquals(listOf(keep.id), merged.alarms.map { it.id })
        assertTrue(merged.deletedAlarmIds.contains(gone.id))

        val otherWay = BackupMerge.merge(cloud, phone)
        assertEquals(listOf(keep.id), otherWay.alarms.map { it.id })
        assertTrue(otherWay.deletedAlarmIds.contains(gone.id))
    }

    @Test
    fun mergeKeepsADismissedAlarmFromRingingAgain() {
        val fired = ClockAlarm(id = "a1", hour = 7, minute = 30, lastFiredAt = 2_000L)
        val stale = ClockAlarm(id = "a1", hour = 7, minute = 30, lastFiredAt = null, snoozeUntil = 500L)
        val phone = BackupDocument(exportedAt = 20, alarms = listOf(fired))
        val cloud = BackupDocument(exportedAt = 30, alarms = listOf(stale))
        val merged = BackupMerge.merge(phone, cloud)
        assertEquals(2_000L, merged.alarms.single().lastFiredAt)
        assertNull(merged.alarms.single().snoozeUntil)

        val otherWay = BackupMerge.merge(cloud, phone)
        assertEquals(2_000L, otherWay.alarms.single().lastFiredAt)
        assertNull(otherWay.alarms.single().snoozeUntil)
    }

    @Test
    fun overwriteWarningNamesLocalData() {
        assertTrue(BackupService.OVERWRITE_WARNING.contains("overwrite any local data"))
        assertTrue(BackupService.OVERWRITE_WARNING.contains("unless the snapshot includes them"))
    }
}

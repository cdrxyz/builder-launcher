package xyz.cdr.builderlauncher.backup

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.LocalItem
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

class BackupDocumentTest {
    @Test
    fun shareOmitsApiKeyAndKeepsTodos() {
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
    fun encryptedPayloadCanCarryApiKey() {
        val settings = BackupSettings.from(
            xyz.cdr.builderlauncher.data.BuilderSettings(apiKey = "sk-secret"),
            includeApiKey = true,
        )
        assertEquals("sk-secret", settings.apiKey)
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
    fun readyRequiresHttpsOrHttpAndAllFields() {
        assertFalse(S3Signer.ready("", "b", "a", "s", "e"))
        assertFalse(S3Signer.ready("https://x", "", "a", "s", "e"))
        assertTrue(S3Signer.ready("https://x", "b", "a", "s", "e"))
        assertTrue(S3Signer.ready("http://10.0.0.5:9000", "b", "a", "s", "e"))
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
}

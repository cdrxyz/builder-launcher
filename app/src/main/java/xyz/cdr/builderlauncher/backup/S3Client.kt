package xyz.cdr.builderlauncher.backup

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class S3Client(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    fun put(
        endpoint: String,
        bucket: String,
        accessKey: String,
        secretKey: String,
        body: ByteArray,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val url = S3Signer.objectUrl(endpoint, bucket)
        val amzDate = amzDate(nowMs)
        val region = S3Signer.regionFor(endpoint)
        val signed = S3Signer.sign(
            method = "PUT",
            url = url,
            accessKey = accessKey,
            secretKey = secretKey,
            region = region,
            payload = body,
            amzDate = amzDate,
            extraHeaders = mapOf("content-type" to BINARY),
        )
        val req = Request.Builder().url(url).put(body.toRequestBody(BINARY_TYPE))
        signed.forEach { (k, v) -> if (k != "host") req.header(headerName(k), v) }
        http.newCall(req.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException(s3Error(resp.code, resp.body?.string().orEmpty(), upload = true))
            }
        }
    }

    fun get(
        endpoint: String,
        bucket: String,
        accessKey: String,
        secretKey: String,
        nowMs: Long = System.currentTimeMillis(),
    ): ByteArray {
        val url = S3Signer.objectUrl(endpoint, bucket)
        val amzDate = amzDate(nowMs)
        val region = S3Signer.regionFor(endpoint)
        val signed = S3Signer.sign(
            method = "GET",
            url = url,
            accessKey = accessKey,
            secretKey = secretKey,
            region = region,
            payload = ByteArray(0),
            amzDate = amzDate,
        )
        val req = Request.Builder().url(url).get()
        signed.forEach { (k, v) -> if (k != "host") req.header(headerName(k), v) }
        http.newCall(req.build()).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful) {
                throw IllegalStateException(s3Error(resp.code, bytes.decodeToString(), upload = false))
            }
            return bytes
        }
    }

    fun probe(
        endpoint: String,
        bucket: String,
        accessKey: String,
        secretKey: String,
        encryptionKey: String = "",
        nowMs: Long = System.currentTimeMillis(),
    ): S3Access.Done {
        if (!S3Signer.credentialsReady(endpoint, bucket, accessKey, secretKey)) {
            return S3AccessReport.missingFields()
        }
        val url = S3Signer.objectUrl(endpoint, bucket)
        val amzDate = amzDate(nowMs)
        val region = S3Signer.regionFor(endpoint)
        val signed = S3Signer.sign(
            method = "GET",
            url = url,
            accessKey = accessKey,
            secretKey = secretKey,
            region = region,
            payload = ByteArray(0),
            amzDate = amzDate,
        )
        val req = Request.Builder().url(url).get()
        signed.forEach { (k, v) -> if (k != "host") req.header(headerName(k), v) }
        return try {
            http.newCall(req.build()).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                if (resp.code != 200) return@use S3AccessReport.fromHttp(resp.code)
                S3AccessReport.decrypt(bytes, encryptionKey)
            }
        } catch (_: Exception) {
            S3AccessReport.unreachable()
        }
    }

    private fun headerName(key: String): String = when (key) {
        "authorization" -> "Authorization"
        "content-type" -> "Content-Type"
        "x-amz-content-sha256" -> "x-amz-content-sha256"
        "x-amz-date" -> "x-amz-date"
        else -> key
    }

    private fun amzDate(nowMs: Long): String {
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(nowMs))
    }

    private fun s3Error(code: Int, body: String, upload: Boolean): String {
        val snippet = body.replace(Regex("\\s+"), " ").take(180)
        return when (code) {
            403 -> "S3 access denied"
            404 -> if (upload) "S3 bucket not found" else "No backup in that bucket"
            else -> "S3 HTTP $code${if (snippet.isBlank()) "" else ": $snippet"}"
        }
    }

    companion object {
        private const val BINARY = "application/octet-stream"
        private val BINARY_TYPE = BINARY.toMediaType()
    }
}

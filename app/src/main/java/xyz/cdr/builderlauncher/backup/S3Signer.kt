package xyz.cdr.builderlauncher.backup

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object S3Signer {
    const val OBJECT_KEY = "builder-launcher/backup.enc"
    private val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~".toSet()

    fun regionFor(endpoint: String): String {
        val host = hostOf(endpoint)
        return when {
            host.endsWith("r2.cloudflarestorage.com") -> "auto"
            host == "s3.amazonaws.com" || host == "s3.dualstack.amazonaws.com" -> "us-east-1"
            host.startsWith("s3.") -> host.split('.').getOrNull(1) ?: "us-east-1"
            else -> "us-east-1"
        }
    }

    fun objectUrl(endpoint: String, bucket: String, key: String = OBJECT_KEY): String {
        val base = endpoint.trim().trimEnd('/')
        return "$base/${bucket.trim().trim('/')}/${key.trimStart('/')}"
    }

    fun ready(endpoint: String, bucket: String, accessKey: String, secretKey: String, encryptionKey: String): Boolean =
        endpoint.isNotBlank() && bucket.isNotBlank() && accessKey.isNotBlank() &&
            secretKey.isNotBlank() && encryptionKey.isNotBlank() &&
            (endpoint.startsWith("https://") || endpoint.startsWith("http://"))

    fun sign(
        method: String,
        url: String,
        accessKey: String,
        secretKey: String,
        region: String,
        payload: ByteArray,
        amzDate: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val host = hostOf(url)
        val uri = pathOf(url)
        val payloadHash = sha256Hex(payload)
        val headers = linkedMapOf(
            "host" to host,
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to amzDate,
        )
        extraHeaders.forEach { (k, v) -> headers[k.lowercase()] = v }
        val signedNames = headers.keys.sorted()
        val canonicalHeaders = signedNames.joinToString("") { "${it}:${headers[it]!!.trim()}\n" }
        val signedHeaderList = signedNames.joinToString(";")
        val canonical = buildString {
            append(method.uppercase())
            append('\n')
            append(uri)
            append('\n')
            append('\n')
            append(canonicalHeaders)
            append('\n')
            append(signedHeaderList)
            append('\n')
            append(payloadHash)
        }
        val dateStamp = amzDate.take(8)
        val scope = "$dateStamp/$region/s3/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonical.toByteArray(Charsets.UTF_8))}"
        val signingKey = signingKey(secretKey, dateStamp, region)
        val signature = hmacHex(signingKey, stringToSign)
        val authorization =
            "AWS4-HMAC-SHA256 Credential=$accessKey/$scope, SignedHeaders=$signedHeaderList, Signature=$signature"
        return headers + ("authorization" to authorization)
    }

    fun uriEncode(input: String, encodeSlash: Boolean): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val out = StringBuilder(bytes.size)
        for (b in bytes) {
            val c = b.toInt() and 0xff
            val ch = c.toChar()
            if (ch in UNRESERVED || (ch == '/' && !encodeSlash)) {
                out.append(ch)
            } else {
                out.append('%')
                out.append("0123456789ABCDEF"[c ushr 4])
                out.append("0123456789ABCDEF"[c and 0x0f])
            }
        }
        return out.toString()
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    internal fun signingKey(secret: String, dateStamp: String, region: String): ByteArray {
        val kDate = hmac(("AWS4$secret").toByteArray(Charsets.UTF_8), dateStamp)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, "s3")
        return hmac(kService, "aws4_request")
    }

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hmacHex(key: ByteArray, data: String): String =
        hmac(key, data).joinToString("") { "%02x".format(it) }

    private fun hostOf(url: String): String {
        val noScheme = url.substringAfter("://", url)
        return noScheme.substringBefore('/').substringBefore('?').lowercase()
    }

    private fun pathOf(url: String): String {
        val noScheme = url.substringAfter("://", url)
        val path = noScheme.substringAfter('/', missingDelimiterValue = "").substringBefore('?')
        if (path.isEmpty()) return "/"
        val encoded = path.split('/').joinToString("/") { uriEncode(it, encodeSlash = true) }
        return "/$encoded"
    }
}

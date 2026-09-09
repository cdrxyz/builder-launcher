package xyz.cdr.builderlauncher.backup

sealed class S3Access {
    data object Idle : S3Access()
    data object Testing : S3Access()
    data class Done(val ok: Boolean, val line: String) : S3Access()
}

object S3AccessReport {
    fun fromHttp(code: Int): S3Access.Done = when (code) {
        200 -> S3Access.Done(true, "S3 access good — backup present")
        404 -> S3Access.Done(true, "S3 access good — no backup yet")
        403 -> S3Access.Done(false, "S3 access denied")
        in 200..299 -> S3Access.Done(true, "S3 access good.")
        else -> S3Access.Done(false, "S3 HTTP $code")
    }

    fun decrypt(body: ByteArray, encryptionKey: String): S3Access.Done {
        if (encryptionKey.isBlank()) {
            return S3Access.Done(true, "S3 access good — backup present. Set encryption key to verify.")
        }
        return try {
            BackupCrypto.decrypt(body, encryptionKey)
            S3Access.Done(true, "S3 access good — backup decrypts")
        } catch (e: IllegalArgumentException) {
            S3Access.Done(false, e.message ?: "Wrong encryption key")
        }
    }

    fun unreachable(): S3Access.Done = S3Access.Done(false, "Could not reach S3.")

    fun missingFields(): S3Access.Done = S3Access.Done(false, "Set endpoint, bucket, and keys.")
}

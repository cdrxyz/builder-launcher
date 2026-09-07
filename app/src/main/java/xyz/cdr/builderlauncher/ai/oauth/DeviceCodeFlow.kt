package xyz.cdr.builderlauncher.ai.oauth

import xyz.cdr.builderlauncher.ai.OAuthSpec

data class DevicePending(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
)

sealed class PollResult {
    data object Pending : PollResult()
    data class SlowDown(val extraSeconds: Int = 5) : PollResult()
    data class Success(val tokens: OAuthTokens) : PollResult()
    data class Failed(val message: String) : PollResult()
}

object DeviceCodeFlow {
    fun request(poster: FormPoster, spec: OAuthSpec.Device): DevicePending {
        val fields = linkedMapOf(
            "client_id" to spec.clientId,
            "scope" to spec.scope,
        )
        fields.putAll(spec.extraDeviceFields)
        val resp = poster.post(spec.deviceUrl, fields, emptyMap())
        if (resp.code !in 200..299) {
            throw IllegalStateException(tokenError(resp.code, resp.body))
        }
        val obj = parseObject(resp.body) ?: throw IllegalStateException("Bad device-code response")
        val deviceCode = obj.str("device_code")
        val userCode = obj.str("user_code")
        val uri = obj.str("verification_uri")
        if (deviceCode.isBlank() || userCode.isBlank() || uri.isBlank()) {
            throw IllegalStateException("Incomplete device-code response")
        }
        if (VerificationPolicy.openUrl(obj.str("verification_uri_complete").ifBlank { null }, uri, spec.allowedHosts) == null) {
            throw IllegalStateException("Provider returned a verification URL that is not allowed")
        }
        return DevicePending(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = uri,
            verificationUriComplete = obj.str("verification_uri_complete").ifBlank { null },
            intervalSeconds = obj.int("interval", 5).coerceAtLeast(1),
            expiresInSeconds = obj.int("expires_in", 300).coerceAtLeast(30),
        )
    }

    fun pollOnce(poster: FormPoster, spec: OAuthSpec.Device, deviceCode: String, nowMs: Long): PollResult {
        val resp = poster.post(
            spec.tokenUrl,
            mapOf(
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                "device_code" to deviceCode,
                "client_id" to spec.clientId,
            ),
            emptyMap(),
        )
        val obj = parseObject(resp.body)
        val err = obj?.str("error").orEmpty()
        if (resp.code in 200..299) {
            val tokens = parseTokens(resp.body, nowMs)
                ?: return PollResult.Failed("Token response missing access_token")
            return PollResult.Success(tokens)
        }
        return when (err) {
            "authorization_pending" -> PollResult.Pending
            "slow_down" -> PollResult.SlowDown()
            "expired_token", "access_denied" -> PollResult.Failed(tokenError(resp.code, resp.body))
            else -> PollResult.Failed(tokenError(resp.code, resp.body))
        }
    }

    fun refresh(poster: FormPoster, spec: OAuthSpec, refreshToken: String, nowMs: Long): OAuthTokens {
        val resp = poster.post(
            spec.tokenUrl,
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to spec.clientId,
            ),
            headers = (spec as? OAuthSpec.PkcePaste)?.headers.orEmpty(),
        )
        if (resp.code !in 200..299) {
            throw IllegalStateException(tokenError(resp.code, resp.body))
        }
        val tokens = parseTokens(resp.body, nowMs)
            ?: throw IllegalStateException("Refresh response missing access_token")
        return if (tokens.refreshToken.isBlank()) tokens.copy(refreshToken = refreshToken) else tokens
    }
}

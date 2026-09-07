package xyz.cdr.builderlauncher.ai.oauth

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val account: String = "",
) {
    fun valid(nowMs: Long, skewMs: Long = 120_000L): Boolean =
        accessToken.isNotBlank() && expiresAtEpochMs - skewMs > nowMs
}

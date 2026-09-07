package xyz.cdr.builderlauncher.ai.oauth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal val OAuthJson = Json { ignoreUnknownKeys = true }

internal fun parseObject(raw: String): JsonObject? =
    runCatching { OAuthJson.parseToJsonElement(raw).jsonObject }.getOrNull()

internal fun JsonObject.str(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

internal fun JsonObject.int(key: String, default: Int): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: default

internal fun JsonObject.long(key: String, default: Long): Long =
    this[key]?.jsonPrimitive?.longOrNull ?: default

internal fun tokenError(code: Int, raw: String): String {
    val obj = parseObject(raw)
    val err = obj?.str("error").orEmpty()
    val desc = obj?.str("error_description").orEmpty()
    val joined = listOf(err, desc).filter { it.isNotBlank() }.joinToString(": ")
    if (joined.isNotBlank()) return joined
    return "HTTP $code ${raw.take(180)}"
}

internal fun parseTokens(raw: String, nowMs: Long): OAuthTokens? {
    val obj = parseObject(raw) ?: return null
    val access = obj.str("access_token")
    if (access.isBlank()) return null
    val refresh = obj.str("refresh_token")
    val expiresIn = obj.long("expires_in", 3600L).coerceAtLeast(60L)
    return OAuthTokens(
        accessToken = access,
        refreshToken = refresh,
        expiresAtEpochMs = nowMs + expiresIn * 1000L,
        account = obj.str("email").ifBlank { obj.str("account") },
    )
}

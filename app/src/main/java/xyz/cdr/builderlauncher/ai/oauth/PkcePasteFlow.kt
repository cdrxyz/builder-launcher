package xyz.cdr.builderlauncher.ai.oauth

import xyz.cdr.builderlauncher.ai.OAuthSpec
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class PkceSession(
    val verifier: String,
    val state: String,
    val authorizeUrl: String,
)

object PkcePasteFlow {
    private val random = SecureRandom()

    fun begin(spec: OAuthSpec.PkcePaste): PkceSession {
        val verifier = randomUrl(32)
        val state = randomUrl(16)
        val challenge = s256(verifier)
        val params = linkedMapOf(
            "response_type" to "code",
            "client_id" to spec.clientId,
            "redirect_uri" to spec.redirectUri,
            "scope" to spec.scope,
            "state" to state,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
        )
        params.putAll(spec.extraAuthorize)
        val query = params.entries.joinToString("&") { (k, v) ->
            "${enc(k)}=${enc(v)}"
        }
        val url = if (spec.authorizeUrl.contains("?")) {
            "${spec.authorizeUrl}&$query"
        } else {
            "${spec.authorizeUrl}?$query"
        }
        if (VerificationPolicy.allowed(spec.authorizeUrl, spec.allowedHosts).not()) {
            throw IllegalStateException("Authorize URL is not allowed")
        }
        return PkceSession(verifier = verifier, state = state, authorizeUrl = url)
    }

    fun complete(
        poster: FormPoster,
        spec: OAuthSpec.PkcePaste,
        session: PkceSession,
        pasted: String,
        nowMs: Long,
    ): OAuthTokens {
        val extracted = extractCode(pasted)
        val pastedState = extracted.state?.let { java.net.URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        val code = java.net.URLDecoder.decode(extracted.code, StandardCharsets.UTF_8.name())
        if (pastedState != null && pastedState != session.state) {
            throw IllegalArgumentException("Sign-in state did not match. Start again.")
        }
        val fields = linkedMapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to spec.redirectUri,
            "client_id" to spec.clientId,
            "code_verifier" to session.verifier,
        )
        val state = extracted.state ?: session.state
        if (state.isNotBlank()) fields["state"] = state
        fields.putAll(spec.extraToken)
        val resp = poster.post(spec.tokenUrl, fields, spec.headers)
        if (resp.code !in 200..299) {
            throw IllegalStateException(tokenError(resp.code, resp.body))
        }
        return parseTokens(resp.body, nowMs)
            ?: throw IllegalStateException("Token response missing access_token")
    }

    data class Extracted(val code: String, val state: String?)

    fun extractCode(pasted: String): Extracted {
        val trimmed = pasted.trim()
        require(trimmed.isNotEmpty()) { "Paste the code from the sign-in page" }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val uri = runCatching { URI(trimmed) }.getOrNull()
                ?: throw IllegalArgumentException("Could not parse the pasted URL")
            val query = uri.query.orEmpty() + "&" + uri.fragment.orEmpty()
            val map = query.split("&").mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
            }.toMap()
            val code = map["code"]?.substringBefore('#')?.ifBlank { null }
                ?: throw IllegalArgumentException("No code in pasted URL")
            val state = map["state"]
            return Extracted(code = code, state = state)
        }
        val hash = trimmed.indexOf('#')
        return if (hash >= 0) {
            Extracted(code = trimmed.substring(0, hash), state = trimmed.substring(hash + 1).ifBlank { null })
        } else {
            Extracted(code = trimmed, state = null)
        }
    }

    private fun randomUrl(bytes: Int): String {
        val buf = ByteArray(bytes)
        random.nextBytes(buf)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
    }

    private fun s256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

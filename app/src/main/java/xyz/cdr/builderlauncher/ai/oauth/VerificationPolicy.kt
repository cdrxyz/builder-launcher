package xyz.cdr.builderlauncher.ai.oauth

import java.net.URI

object VerificationPolicy {
    fun allowed(uri: String, hosts: Set<String>): Boolean {
        val parsed = runCatching { URI(uri.trim()) }.getOrNull() ?: return false
        if (parsed.scheme?.lowercase() != "https") return false
        if (!parsed.userInfo.isNullOrEmpty()) return false
        val host = parsed.host?.lowercase() ?: return false
        val port = parsed.port
        if (port != -1 && port != 443) return false
        return hosts.any { allowed -> host == allowed || host.endsWith(".$allowed") }
    }

    fun openUrl(uriComplete: String?, uri: String, hosts: Set<String>): String? {
        val complete = uriComplete?.trim().orEmpty()
        if (complete.isNotEmpty() && allowed(complete, hosts)) return complete
        val base = uri.trim()
        if (base.isNotEmpty() && allowed(base, hosts)) return base
        return null
    }
}

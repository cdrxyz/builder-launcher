package xyz.cdr.builderlauncher.ai

import java.net.URI

object EndpointPolicy {
    fun allowed(base: String): Boolean {
        val uri = runCatching { URI(base) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "https") return true
        if (scheme != "http") return false
        val host = uri.host ?: return false
        return isPrivateHost(host)
    }

    fun isPrivateHost(host: String): Boolean {
        val h = host.trim().lowercase()
        if (h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "10.0.2.2") return true
        val parts = h.split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() == null }) return false
        val a = parts[0].toInt()
        val b = parts[1].toInt()
        return when (a) {
            10 -> true
            192 -> b == 168
            172 -> b in 16..31
            else -> false
        }
    }
}

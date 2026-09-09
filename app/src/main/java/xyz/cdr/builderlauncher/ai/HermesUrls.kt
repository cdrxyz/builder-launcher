package xyz.cdr.builderlauncher.ai

import xyz.cdr.builderlauncher.data.BuilderSettings
import java.net.URI

object HermesUrls {
    const val API_PORT = 8642
    const val WEBUI_PORT = 9119
    const val DEFAULT_API = "http://192.168.1.10:8642"
    const val DEFAULT_WEBUI = "http://192.168.1.10:9119"

    private val webUiPorts = setOf(WEBUI_PORT, 8787, 5173)

    fun looksLikeWebUi(raw: String): Boolean {
        val uri = parse(raw) ?: return false
        return effectivePort(uri) in webUiPorts
    }

    fun apiBase(settings: BuilderSettings): String {
        val base = trim(settings.hermesBaseUrl)
        if (base.isNotEmpty() && !looksLikeWebUi(base)) return base
        val web = webUi(settings)
        if (web.isNotEmpty()) return withPort(web, API_PORT) ?: web
        return base
    }

    fun webUi(settings: BuilderSettings): String {
        val explicit = trim(settings.hermesWebUrl)
        if (explicit.isNotEmpty()) return explicit
        val base = trim(settings.hermesBaseUrl)
        if (base.isEmpty()) return ""
        if (looksLikeWebUi(base)) return base
        return withPort(base, WEBUI_PORT).orEmpty()
    }

    fun openInBrowser(settings: BuilderSettings): String? {
        val web = webUi(settings)
        if (web.isNotEmpty()) return "$web/"
        return null
    }

    fun webUiPlaceholder(api: String): String {
        val trimmed = trim(api)
        if (trimmed.isNotEmpty() && !looksLikeWebUi(trimmed)) {
            withPort(trimmed, WEBUI_PORT)?.let { return it }
        }
        return DEFAULT_WEBUI
    }

    fun hasHost(settings: BuilderSettings): Boolean =
        trim(settings.hermesBaseUrl).isNotEmpty() || trim(settings.hermesWebUrl).isNotEmpty()

    private fun trim(raw: String): String = raw.trim().trimEnd('/')

    private fun parse(raw: String): URI? {
        val trimmed = trim(raw)
        if (trimmed.isEmpty()) return null
        return runCatching { URI(trimmed) }.getOrNull()?.takeIf { !it.host.isNullOrBlank() }
    }

    private fun effectivePort(uri: URI): Int {
        if (uri.port != -1) return uri.port
        return when (uri.scheme?.lowercase()) {
            "https" -> 443
            "http" -> 80
            else -> -1
        }
    }

    private fun withPort(raw: String, port: Int): String? {
        val uri = parse(raw) ?: return null
        val host = uri.host ?: return null
        return runCatching {
            URI(uri.scheme, uri.userInfo, host, port, null, null, null).toString().trimEnd('/')
        }.getOrNull()
    }
}

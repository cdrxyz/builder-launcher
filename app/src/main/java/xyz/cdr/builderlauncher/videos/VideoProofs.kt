package xyz.cdr.builderlauncher.videos

import java.net.URLEncoder

data class VideoChallenge(
    val visitorData: String,
    val clientVersion: String,
    val interpreterUrl: String,
    val program: String,
    val globalName: String,
)

data class VideoProof(
    val visitorData: String,
    val clientVersion: String,
    val playerPot: String,
    val streamingPot: String,
)

object VideoProofs {
    const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    const val HOME = "https://www.youtube.com/"

    fun parse(page: String): VideoChallenge? {
        val visitor = field(page, "visitorData") ?: return null
        val version = field(page, "INNERTUBE_CLIENT_VERSION") ?: return null
        val start = page.indexOf("bgChallenge")
        if (start < 0) return null
        val blob = unescape(page.substring(start, minOf(page.length, start + 400_000)))
        val interpreter = wrapped(blob, "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue") ?: return null
        val program = field(blob, "program") ?: return null
        val globalName = field(blob, "globalName") ?: return null
        val url = if (interpreter.startsWith("//")) "https:$interpreter" else interpreter
        if (visitor.isBlank() || program.isBlank() || globalName.isBlank() || url.isBlank()) return null
        return VideoChallenge(visitor, version, url, program, globalName)
    }

    fun webPlayerBody(id: String, proof: VideoProof): String =
        """{"context":{"client":{"clientName":"WEB","clientVersion":${json(proof.clientVersion)},"hl":"en","gl":"US","visitorData":${json(proof.visitorData)}}},"videoId":${json(id)},"contentCheckOk":true,"racyCheckOk":true,"serviceIntegrityDimensions":{"poToken":${json(proof.playerPot)}}}"""

    fun withStreamProof(url: String, pot: String): String {
        if (url.isBlank() || pot.isBlank() || url.contains("pot=")) return url
        val join = if (url.contains("?")) "&" else "?"
        return url + join + "pot=" + URLEncoder.encode(pot, Charsets.UTF_8.name())
    }

    fun apply(picked: PickedVideo, pot: String): PickedVideo = picked.copy(
        audioUrl = withStreamProof(picked.audioUrl, pot),
        videoUrl = withStreamProof(picked.videoUrl, pot),
    )

    private fun field(text: String, name: String): String? {
        val marker = "$name\\x22:\\x22"
        val escaped = text.indexOf(marker)
        if (escaped >= 0) {
            val start = escaped + marker.length
            val end = text.indexOf("\\x22", start)
            if (end > start) return unescape(text.substring(start, end))
        }
        val plain = "\"$name\":\""
        val at = text.indexOf(plain)
        if (at < 0) return null
        val start = at + plain.length
        val end = text.indexOf('"', start)
        if (end <= start) return null
        return text.substring(start, end)
    }

    private fun wrapped(text: String, name: String): String? = field(text, name)

    fun unescape(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '\\' && i + 1 < raw.length) {
                when (raw[i + 1]) {
                    'x' -> if (i + 3 < raw.length) {
                        val hex = raw.substring(i + 2, i + 4)
                        val value = hex.toIntOrNull(16)
                        if (value != null) {
                            out.append(value.toChar())
                            i += 4
                            continue
                        }
                    }
                    '/' -> {
                        out.append('/')
                        i += 2
                        continue
                    }
                    '\\' -> {
                        out.append('\\')
                        i += 2
                        continue
                    }
                }
            }
            out.append(raw[i])
            i += 1
        }
        return out.toString()
    }

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                else -> append(ch)
            }
        }
        append('"')
    }
}

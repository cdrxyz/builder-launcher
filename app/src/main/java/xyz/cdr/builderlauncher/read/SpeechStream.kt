package xyz.cdr.builderlauncher.read

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import xyz.cdr.builderlauncher.net.HttpClients
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

class SpeechStream(
    private val client: OkHttpClient = HttpClients.derived(connectSec = 20, readSec = 360, writeSec = 60),
) {
    fun open(bearer: String, text: String, file: File, voice: String = Reads.GROK_VOICE, onBytes: (Int) -> Unit, onDone: (Throwable?) -> Unit): WebSocket {
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        file.createNewFile()
        val request = Request.Builder()
            .url(Reads.speechSocketUrl(voice))
            .header("Authorization", "Bearer $bearer")
            .build()
        val sink = FileOutputStream(file, true).buffered()
        var received = 0
        var finished = false
        fun finish(err: Throwable?) {
            if (finished) return
            finished = true
            runCatching { sink.close() }
            onDone(err)
        }
        return client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                for (frame in Reads.speechTextFrames(text)) webSocket.send(frame)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val (type, payload) = Reads.speechEvent(text)
                when (type) {
                    "audio.delta" -> {
                        if (payload.isEmpty()) return
                        val bytes = Base64.getDecoder().decode(payload)
                        sink.write(bytes)
                        sink.flush()
                        received += bytes.size
                        onBytes(received)
                    }
                    "audio.done" -> {
                        finish(null)
                        webSocket.close(1000, null)
                    }
                    "error" -> {
                        finish(IllegalStateException(payload.ifBlank { "Could not prepare audio." }))
                        webSocket.close(1000, null)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(t)
            }
        })
    }

    companion object {
        fun waitForFirstAudio(file: File, timeoutMs: Long = 20_000): Boolean {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
            while (System.nanoTime() < deadline) {
                if (file.length() >= Reads.SPEECH_START_BYTES) return true
                Thread.sleep(40)
            }
            return file.length() >= Reads.SPEECH_START_BYTES
        }
    }
}

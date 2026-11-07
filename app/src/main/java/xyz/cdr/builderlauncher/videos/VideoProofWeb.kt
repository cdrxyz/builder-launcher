package xyz.cdr.builderlauncher.videos

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import xyz.cdr.builderlauncher.net.HttpClients

object VideoProofWeb {
    private const val WAIT_MS = 20_000L
    private val main = Handler(Looper.getMainLooper())

    suspend fun mint(context: Context, videoId: String, http: OkHttpClient = HttpClients.derived(15, 20)): VideoProof? {
        val page = get(http, VideoProofs.HOME, VideoProofs.WEB_UA) ?: return null
        val challenge = VideoProofs.parse(page) ?: return null
        val script = get(http, challenge.interpreterUrl, VideoProofs.WEB_UA) ?: return null
        return withTimeoutOrNull(WAIT_MS) {
            val session = open(context, script, challenge) ?: return@withTimeoutOrNull null
            try {
                val botguard = session.snapshot.await() ?: return@withTimeoutOrNull null
                val integrity = integrityToken(http, botguard) ?: return@withTimeoutOrNull null
                val streaming = session.mint(integrity, challenge.visitorData) ?: return@withTimeoutOrNull null
                val player = session.mint(integrity, videoId) ?: return@withTimeoutOrNull null
                VideoProof(challenge.visitorData, challenge.clientVersion, player, streaming)
            } finally {
                session.close()
            }
        }
    }

    private class Session(
        val web: WebView,
        val snapshot: kotlinx.coroutines.CompletableDeferred<String?>,
    ) {
        var token = kotlinx.coroutines.CompletableDeferred<String?>()

        suspend fun mint(integrity: String, binding: String): String? = withContext(Dispatchers.Main) {
            token = kotlinx.coroutines.CompletableDeferred()
            web.evaluateJavascript("mint(${json(integrity)}, ${json(binding)})", null)
            withTimeoutOrNull(WAIT_MS) { token.await() }
        }

        fun close() {
            main.post { web.destroy() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun open(context: Context, script: String, challenge: VideoChallenge): Session? =
        withContext(Dispatchers.Main) {
            val snapshot = kotlinx.coroutines.CompletableDeferred<String?>()
            val web = WebView(context.applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
            }
            val session = Session(web, snapshot)
            val bridge = object {
                @JavascriptInterface
                fun onSnapshot(value: String) {
                    snapshot.complete(value.ifBlank { null })
                }

                @JavascriptInterface
                fun onError(value: String) {
                    snapshot.complete(null)
                }

                @JavascriptInterface
                fun onToken(value: String) {
                    session.token.complete(value.ifBlank { null })
                }
            }
            web.addJavascriptInterface(bridge, "VideoProof")
            web.loadDataWithBaseURL("https://www.youtube.com/", page(script, challenge), "text/html", "utf-8", null)
            session
        }

    private fun get(http: OkHttpClient, url: String, ua: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", ua).build()
        return runCatching {
            http.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull()
    }

    private fun integrityToken(http: OkHttpClient, botguard: String): String? {
        val body = """["${VideoProofs.REQUEST_KEY}",${json(botguard)}]"""
        val request = Request.Builder()
            .url("https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/GenerateIT")
            .header("Content-Type", "application/json+protobuf")
            .header("x-goog-api-key", "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw")
            .header("x-user-agent", "grpc-web-javascript/0.1")
            .post(body.toRequestBody("application/json+protobuf".toMediaType()))
            .build()
        val text = runCatching {
            http.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return null
        val token = text.trim().removePrefix("[").substringBefore(',').trim().trim('"')
        return token.ifBlank { null }
    }

    private fun page(script: String, challenge: VideoChallenge): String {
        val safeScript = script.replace("</", "<\\/")
        val program = json(challenge.program)
        val name = json(challenge.globalName)
        return """
            <!doctype html><html><head><meta charset="utf-8"></head><body>
            <script>$safeScript</script>
            <script>
            function b64(bytes) {
              var s = '';
              for (var i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
              return btoa(s);
            }
            async function mint(integrityB64, binding) {
              try {
                var raw = atob(integrityB64);
                var bytes = new Uint8Array(raw.length);
                for (var i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
                var getMinter = window.__webPo && window.__webPo[0];
                if (!getMinter) throw new Error('no minter');
                var mintCallback = await getMinter(bytes);
                var result = await mintCallback(new TextEncoder().encode(binding));
                VideoProof.onToken(b64(result));
              } catch (err) {
                VideoProof.onToken('');
              }
            }
            async function start() {
              try {
                var vm = window[$name];
                if (!vm || !vm.a) throw new Error('no vm');
                var fns = {};
                await vm.a($program, function(asyncSnapshot) { fns.asyncSnapshot = asyncSnapshot; }, true, undefined, function() {}, [[], []], undefined, false, undefined);
                var webPo = [];
                var botguard = await new Promise(function(resolve, reject) {
                  if (!fns.asyncSnapshot) { reject(new Error('no snapshot')); return; }
                  fns.asyncSnapshot(function(response) { resolve(response); }, [undefined, undefined, webPo, undefined]);
                });
                window.__webPo = webPo;
                VideoProof.onSnapshot(String(botguard || ''));
              } catch (err) {
                VideoProof.onError(String(err && err.message || err));
              }
            }
            start();
            </script></body></html>
        """.trimIndent()
    }

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(ch)
            }
        }
        append('"')
    }
}

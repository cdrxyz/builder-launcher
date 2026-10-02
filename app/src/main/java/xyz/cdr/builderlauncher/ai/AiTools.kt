package xyz.cdr.builderlauncher.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import xyz.cdr.builderlauncher.commands.Calculator
import xyz.cdr.builderlauncher.data.ChatMessage
import xyz.cdr.builderlauncher.data.LlmProvider
import java.net.URI

object AiTools {
    const val SYSTEM =
        "You are a concise assistant on a builder's phone. Prefer short answers they can act on. Use markdown when it helps: headings, lists, tables, and fenced code. Skip preamble. You can search the web, open a public page, and calculate. Use those for current facts and arithmetic. Do not invent sources."
    const val MAX_ROUNDS = 2
    const val SEARCH_UA = "BuilderLauncher/0.1 (+https://cdr.xyz)"

    private val json = Json { ignoreUnknownKeys = true }
    private val storeFalse = setOf(
        LlmProvider.OPENAI,
        LlmProvider.GENERIC,
        LlmProvider.GEMINI,
        LlmProvider.GROQ,
        LlmProvider.XAI,
        LlmProvider.DEEPSEEK,
        LlmProvider.MISTRAL,
    )

    data class Call(val id: String, val name: String, val arguments: String)
    data class Turn(val text: String, val calls: List<Call>)
    data class Drop(val privacy: Boolean, val tools: Boolean)
    data class Hit(val title: String, val url: String, val snippet: String)

    fun openaiRequest(
        model: String,
        messages: List<JsonObject>,
        provider: LlmProvider,
        privacy: Boolean,
        tools: Boolean,
        stream: Boolean,
    ): String = buildJsonObject {
        put("model", model)
        put("messages", JsonArray(messages))
        put("max_tokens", 2048)
        put("temperature", 0.4)
        put("stream", stream)
        if (privacy) {
            for ((key, value) in privacyFields(provider)) put(key, value)
        }
        if (tools) {
            put("tools", openaiTools())
            put("tool_choice", "auto")
        }
    }.toString()

    fun anthropicRequest(
        model: String,
        messages: List<JsonObject>,
        tools: Boolean,
        stream: Boolean,
    ): String = buildJsonObject {
        put("model", model)
        put("max_tokens", 2048)
        put("stream", stream)
        put("system", SYSTEM)
        put("messages", JsonArray(messages))
        if (tools) put("tools", anthropicTools())
    }.toString()

    fun textMessages(messages: List<ChatMessage>, system: Boolean): List<JsonObject> {
        val rows = mutableListOf<JsonObject>()
        if (system) {
            rows += buildJsonObject {
                put("role", "system")
                put("content", SYSTEM)
            }
        }
        messages.forEach { msg ->
            rows += buildJsonObject {
                put("role", if (msg.fromUser) "user" else "assistant")
                put("content", msg.content)
            }
        }
        return rows
    }

    fun appendOpenAiTools(messages: List<JsonObject>, turn: Turn, results: List<Pair<String, String>>): List<JsonObject> {
        val assistant = buildJsonObject {
            put("role", "assistant")
            put("content", if (turn.text.isEmpty()) JsonNull else JsonPrimitive(turn.text))
            put("tool_calls", buildJsonArray {
                for (call in turn.calls) {
                    add(buildJsonObject {
                        put("id", call.id)
                        put("type", "function")
                        put("function", buildJsonObject {
                            put("name", call.name)
                            put("arguments", call.arguments)
                        })
                    })
                }
            })
        }
        val rows = results.map { (id, content) ->
            buildJsonObject {
                put("role", "tool")
                put("tool_call_id", id)
                put("content", content.take(6000))
            }
        }
        return messages + assistant + rows
    }

    fun appendAnthropicTools(messages: List<JsonObject>, turn: Turn, results: List<Pair<String, String>>): List<JsonObject> {
        val blocks = buildJsonArray {
            if (turn.text.isNotEmpty()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", turn.text)
                })
            }
            for (call in turn.calls) {
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", call.id)
                    put("name", call.name)
                    put("input", jsonObject(call.arguments))
                })
            }
        }
        val assistant = buildJsonObject {
            put("role", "assistant")
            put("content", blocks)
        }
        val user = buildJsonObject {
            put("role", "user")
            put("content", buildJsonArray {
                for ((id, content) in results) {
                    add(buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", id)
                        put("content", content.take(6000))
                    })
                }
            })
        }
        return messages + assistant + user
    }

    fun parseBody(raw: String, openai: Boolean): Turn? {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val accum = TurnAccum(openai)
        accum.acceptBody(root)
        return accum.turn().takeIf { it.text.isNotEmpty() || it.calls.isNotEmpty() }
    }

    fun droppedFields(errorText: String): Drop {
        val lower = errorText.lowercase()
        val unknown = Regex("unknown|unrecognized|unexpected|not supported|not a valid|invalid parameter|extra field|additional propert").containsMatchIn(lower)
        if (!unknown) return Drop(privacy = false, tools = false)
        return Drop(
            privacy = Regex("store|data_collection|\\bzdr\\b|provider").containsMatchIn(lower),
            tools = lower.contains("tool"),
        )
    }

    fun publicPageUrl(raw: String): String? {
        val value = raw.trim()
        if (!isPublicHttps(value)) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.userInfo.isNullOrEmpty()) return null
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "https://${uri.host}${if (uri.port > 0 && uri.port != 443) ":${uri.port}" else ""}$path$query"
    }

    fun parseDuckDuckGo(html: String): List<Hit> {
        val hits = mutableListOf<Hit>()
        val re = Regex("""<a\b([^>]*class=['"][^'"]*(?:result__a|result-link)[^'"]*['"][^>]*)>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
        for (match in re.findAll(html)) {
            if (hits.size >= 5) break
            val href = Regex("""href=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(match.groupValues[1])?.groupValues?.get(1).orEmpty()
            val url = publicPageUrl(unwrapDdg(href)) ?: continue
            val after = html.substring(match.range.last + 1, minOf(html.length, match.range.last + 801))
            val snippet = Regex("""<(?:a|td)\b[^>]*class=['"][^'"]*result[_-]snippet[^'"]*['"][^>]*>([\s\S]*?)</(?:a|td)>""", RegexOption.IGNORE_CASE)
                .find(after)
                ?.groupValues
                ?.get(1)
            hits += Hit(
                title = decodeEntities(match.groupValues[2]).take(160).ifBlank { url },
                url = url,
                snippet = snippet?.let { decodeEntities(it).take(240) }.orEmpty(),
            )
        }
        return hits
    }

    fun formatHits(hits: List<Hit>): String {
        if (hits.isEmpty()) return "No results."
        return hits.mapIndexed { index, hit ->
            buildString {
                append("${index + 1}. ${hit.title}\n   ${hit.url}")
                if (hit.snippet.isNotEmpty()) append("\n   ${hit.snippet}")
            }
        }.joinToString("\n")
    }

    fun stripHtml(html: String): String = html
        .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&nbsp;", " ")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun calculate(raw: String): String = Calculator.preview(raw) ?: "Could not calculate."

    data class ToolResult(val text: String, val sites: List<String> = emptyList(), val failed: Boolean = false)

    fun run(name: String, argsJson: String, get: (String) -> String?): String = perform(name, argsJson, get).text

    fun perform(name: String, argsJson: String, get: (String) -> String?): ToolResult {
        val args = runCatching { json.parseToJsonElement(argsJson).jsonObject }.getOrNull()
            ?: return ToolResult("Invalid tool arguments.", failed = true)
        return when (name) {
            "web_search" -> searchResult(args["query"]?.jsonPrimitive?.contentOrNull.orEmpty(), get)
            "web_fetch" -> {
                val raw = args["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val host = hostOf(publicPageUrl(raw).orEmpty())
                val text = fetchPage(raw, get)
                val failed = text == "Page could not be read." || text == "Only public HTTPS pages."
                ToolResult(text, listOfNotNull(host), failed)
            }
            "calculate" -> ToolResult(calculate(args["expression"]?.jsonPrimitive?.contentOrNull.orEmpty()))
            else -> ToolResult("Unknown tool.", failed = true)
        }
    }

    fun toolStep(calls: List<Call>, round: Int, tools: Boolean): String = when {
        calls.isEmpty() || !tools -> "answer"
        round >= MAX_ROUNDS -> "finish"
        else -> "run"
    }

    fun activity(calls: List<Call>, sites: List<String> = emptyList(), failed: Boolean = false): List<String> {
        val looking = calls.filter { it.name == "web_search" || it.name == "web_fetch" }
        if (looking.isEmpty()) return emptyList()
        if (failed && sites.isEmpty()) return listOf("Search failed")
        val heading = if (looking.any { it.name == "web_search" }) "Searching" else "Reading"
        val queries = looking.mapNotNull { queryLabel(it) }.distinct().take(4)
        val siteLines = sites.map { "· $it" }.distinct().take(8)
        val tail = if (failed) listOf("Search failed") else emptyList()
        return listOf(heading) + queries + siteLines + tail
    }

    fun hosts(hits: List<Hit>): List<String> = hits.mapNotNull { hostOf(it.url) }.distinct()

    fun shortFailure(raw: String): String {
        val flat = raw.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        if (flat.length in 1..60 && !flat.contains('<') && !flat.contains('{') && !flat.contains("http", ignoreCase = true)) {
            return flat
        }
        return "Search failed."
    }

    fun shortReason(raw: String): String {
        val flat = runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            val message = root["message"]?.jsonPrimitive?.contentOrNull
            val err = root["error"]
            val fromErr = runCatching { err?.jsonPrimitive?.contentOrNull }.getOrNull()
                ?: err?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            (message ?: fromErr)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        }.getOrDefault("")
        if (flat.length in 1..60 && flat.none { it == '<' || it == '{' || it == '\n' }) return flat
        return "request failed"
    }

    fun chatDisplay(source: String): String {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return source
        if (isDump(trimmed)) return if (looksLikeSearchDump(trimmed)) "Search failed." else "Could not reach the model."
        if (trimmed.length > 12_000) return trimmed.take(12_000).trimEnd() + "…"
        return source
    }

    fun searchUrl(query: String): String? {
        val q = query.trim().take(200)
        if (q.isEmpty()) return null
        return "https://lite.duckduckgo.com/lite/?q=${java.net.URLEncoder.encode(q, "UTF-8")}"
    }

    private fun searchResult(query: String, get: (String) -> String?): ToolResult {
        val url = searchUrl(query) ?: return ToolResult("Need a query.", failed = true)
        val html = get(url) ?: return ToolResult("Search failed.", failed = true)
        if (looksLikeErrorPage(html)) return ToolResult("Search failed.", failed = true)
        val hits = parseDuckDuckGo(html)
        return ToolResult(formatHits(hits), hosts(hits))
    }

    private fun looksLikeErrorPage(html: String): Boolean {
        val lower = html.lowercase()
        if (lower.contains("result-link") || lower.contains("result__a")) return false
        if (lower.contains("captcha") || lower.contains("access denied")) return true
        return html.length > 8_000 && (lower.contains("<html") || lower.contains("<!doctype"))
    }

    private fun queryLabel(call: Call): String? {
        val args = runCatching { json.parseToJsonElement(call.arguments).jsonObject }.getOrNull()
        return when (call.name) {
            "web_search" -> args?.get("query")?.jsonPrimitive?.contentOrNull?.trim()?.take(80)?.ifBlank { null }
            "web_fetch" -> hostOf(args?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty())
            else -> null
        }
    }

    private fun hostOf(raw: String): String? {
        val host = runCatching { URI(raw).host }.getOrNull()?.lowercase()?.removePrefix("www.").orEmpty()
        return host.ifBlank { null }
    }

    private fun isDump(text: String): Boolean {
        val lower = text.lowercase()
        if (lower.contains("<html") || lower.contains("<!doctype")) return true
        if (text.startsWith("LLM error") && text.length > 160) return true
        if (text.startsWith("{") && text.length > 400) return true
        return text.length > 12_000
    }

    private fun looksLikeSearchDump(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("<html") || lower.contains("<!doctype") || lower.contains("duckduckgo") || lower.contains("search failed")
    }

    private fun fetchPage(raw: String, get: (String) -> String?): String {
        val url = publicPageUrl(raw) ?: return "Only public HTTPS pages."
        val body = get(url) ?: return "Page could not be read."
        if (looksLikeErrorPage(body)) return "Page could not be read."
        val plain = if (body.trimStart().startsWith("{") || body.trimStart().startsWith("[")) body else stripHtml(body)
        return plain.replace(Regex("\\s+"), " ").trim().take(6000).ifBlank { "Page was empty." }
    }

    private fun privacyFields(provider: LlmProvider): Map<String, kotlinx.serialization.json.JsonElement> {
        if (provider == LlmProvider.OPENROUTER) {
            return mapOf(
                "provider" to buildJsonObject {
                    put("zdr", true)
                    put("data_collection", "deny")
                },
            )
        }
        if (provider in storeFalse) return mapOf("store" to JsonPrimitive(false))
        return emptyMap()
    }

    private fun openaiTools(): JsonArray = buildJsonArray {
        for ((name, description, required, prop) in toolDefs()) {
            add(buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", name)
                    put("description", description)
                    put("parameters", schema(required, prop))
                })
            })
        }
    }

    private fun anthropicTools(): JsonArray = buildJsonArray {
        for ((name, description, required, prop) in toolDefs()) {
            add(buildJsonObject {
                put("name", name)
                put("description", description)
                put("input_schema", schema(required, prop))
            })
        }
    }

    private fun schema(required: String, prop: String): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put(required, buildJsonObject {
                put("type", "string")
                put("description", prop)
            })
        })
        put("required", buildJsonArray { add(JsonPrimitive(required)) })
        put("additionalProperties", false)
    }

    private fun toolDefs() = listOf(
        ToolDef("web_search", "Search the public web. Use for current facts, news, and anything you are not sure of.", "query", "Search query"),
        ToolDef("web_fetch", "Read the text of one public HTTPS page.", "url", "https URL"),
        ToolDef("calculate", "Evaluate an arithmetic expression. No variables.", "expression", "Arithmetic expression"),
    )

    private data class ToolDef(val name: String, val description: String, val required: String, val prop: String)

    private fun jsonObject(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw).jsonObject }.getOrDefault(buildJsonObject {})

    private fun unwrapDdg(href: String): String {
        val decoded = href.replace("&amp;", "&")
        val absolute = if (decoded.startsWith("//")) "https:$decoded" else decoded
        val uri = runCatching { URI(absolute) }.getOrNull() ?: return ""
        val query = uri.rawQuery.orEmpty()
        val uddg = query.split('&').firstOrNull { it.startsWith("uddg=") }?.substringAfter("uddg=")
        if (!uddg.isNullOrBlank()) return java.net.URLDecoder.decode(uddg, Charsets.UTF_8)
        if (uri.scheme.equals("https", true) && !uri.host.orEmpty().endsWith("duckduckgo.com")) return uri.toString()
        return ""
    }

    private fun decodeEntities(text: String): String = text
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: it.value }
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isPublicHttps(raw: String): Boolean {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", true)) return false
        if (!uri.userInfo.isNullOrEmpty()) return false
        val host = uri.host?.lowercase() ?: return false
        if (host.isEmpty() || host == "localhost" || host.endsWith(".local") || host == "metadata.google.internal") return false
        if (host.contains(':')) return false
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            if (a == 10 || a == 127 || a == 0 || (a == 192 && b == 168) || (a == 172 && b in 16..31) || (a == 169 && b == 254)) {
                return false
            }
        }
        return true
    }

    class TurnAccum(private val openai: Boolean) {
        private val text = StringBuilder()
        private val calls = linkedMapOf<Int, CallBuilder>()

        fun acceptData(data: String) {
            val trimmed = data.trim()
            if (trimmed.isEmpty() || trimmed == "[DONE]") return
            val root = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: return
            if (openai) acceptOpenAi(root) else acceptAnthropic(root)
        }

        fun acceptBody(root: JsonObject) {
            if (openai) acceptOpenAi(root) else acceptAnthropic(root)
        }

        fun turn(): Turn = Turn(
            text = text.toString(),
            calls = calls.values.mapNotNull { it.call() },
        )

        private fun acceptOpenAi(root: JsonObject) {
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return
            val source = choice["delta"]?.jsonObject ?: choice["message"]?.jsonObject ?: return
            source["content"]?.jsonPrimitive?.contentOrNull?.let { text.append(it) }
            val rows = source["tool_calls"]?.jsonArray ?: return
            rows.forEachIndexed { fallback, el ->
                val obj = el.jsonObject
                val index = obj["index"]?.jsonPrimitive?.intOrNull ?: fallback
                val builder = calls.getOrPut(index) { CallBuilder() }
                obj["id"]?.jsonPrimitive?.contentOrNull?.let { builder.id = it }
                val fn = obj["function"]?.jsonObject ?: return@forEachIndexed
                fn["name"]?.jsonPrimitive?.contentOrNull?.let { builder.name = it }
                fn["arguments"]?.jsonPrimitive?.contentOrNull?.let { builder.args.append(it) }
            }
        }

        private fun acceptAnthropic(root: JsonObject) {
            when (root["type"]?.jsonPrimitive?.contentOrNull) {
                "content_block_start" -> {
                    val block = root["content_block"]?.jsonObject ?: return
                    if (block["type"]?.jsonPrimitive?.contentOrNull != "tool_use") return
                    val index = root["index"]?.jsonPrimitive?.intOrNull ?: calls.size
                    calls[index] = CallBuilder().apply {
                        id = block["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        name = block["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    }
                }
                "content_block_delta" -> {
                    val index = root["index"]?.jsonPrimitive?.intOrNull ?: 0
                    val delta = root["delta"]?.jsonObject ?: return
                    when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                        "text_delta" -> text.append(delta["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                        "input_json_delta" -> calls[index]?.args?.append(delta["partial_json"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    }
                }
                else -> {
                    val blocks = root["content"]?.jsonArray ?: return
                    blocks.forEachIndexed { index, el ->
                        val block = el.jsonObject
                        when (block["type"]?.jsonPrimitive?.contentOrNull) {
                            "text" -> text.append(block["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                            "tool_use" -> calls[index] = CallBuilder().apply {
                                id = block["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                name = block["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                args.append(block["input"]?.toString().orEmpty().ifBlank { "{}" })
                            }
                        }
                    }
                }
            }
        }
    }

    class CallBuilder {
        var id: String = ""
        var name: String = ""
        val args = StringBuilder()

        fun call(): Call? {
            if (name.isBlank()) return null
            return Call(id.ifBlank { "call_$name" }, name, args.toString().ifBlank { "{}" })
        }
    }
}

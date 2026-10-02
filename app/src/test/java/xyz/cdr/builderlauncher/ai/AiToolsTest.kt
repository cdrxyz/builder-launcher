package xyz.cdr.builderlauncher.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.data.ChatMessage
import xyz.cdr.builderlauncher.data.LlmProvider

class AiToolsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun openaiRequestSendsStoreFalseAndTools() {
        val body = json.parseToJsonElement(
            AiTools.openaiRequest(
                model = "gpt-4o",
                messages = AiTools.textMessages(listOf(ChatMessage(role = "user", content = "hi")), system = true),
                provider = LlmProvider.OPENAI,
                privacy = true,
                tools = true,
                stream = false,
            ),
        ).jsonObject
        assertEquals(false, body["store"]?.jsonPrimitive?.boolean)
        assertEquals("web_search", body["tools"]!!.jsonArray[0].jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("calculate", body["tools"]!!.jsonArray[2].jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun openRouterRequiresZeroDataRetention() {
        val body = json.parseToJsonElement(
            AiTools.openaiRequest(
                model = "openrouter/auto",
                messages = AiTools.textMessages(listOf(ChatMessage(role = "user", content = "hi")), system = true),
                provider = LlmProvider.OPENROUTER,
                privacy = true,
                tools = true,
                stream = false,
            ),
        ).jsonObject
        val provider = body["provider"]!!.jsonObject
        assertEquals(true, provider["zdr"]!!.jsonPrimitive.boolean)
        assertEquals("deny", provider["data_collection"]!!.jsonPrimitive.content)
        assertNull(body["store"])
    }

    @Test
    fun localAndAnthropicDoNotInventPrivacyFlags() {
        val ollama = json.parseToJsonElement(
            AiTools.openaiRequest("llama3.2", AiTools.textMessages(emptyList(), system = true), LlmProvider.OLLAMA, privacy = true, tools = true, stream = false),
        ).jsonObject
        assertNull(ollama["store"])
        assertNull(ollama["provider"])
        val claude = json.parseToJsonElement(
            AiTools.anthropicRequest("claude-sonnet-4-5", AiTools.textMessages(listOf(ChatMessage("user", "hi")), system = false), tools = true, stream = false),
        ).jsonObject
        assertNull(claude["store"])
        assertEquals("web_fetch", claude["tools"]!!.jsonArray[1].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun parsesToolCallsAndAppendsResults() {
        val raw = """{"choices":[{"message":{"content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"web_search","arguments":"{\"query\":\"cedar\"}"}}]}}]}"""
        val turn = AiTools.parseBody(raw, openai = true)!!
        assertEquals("web_search", turn.calls[0].name)
        val next = AiTools.appendOpenAiTools(
            AiTools.textMessages(listOf(ChatMessage("user", "hi")), system = false),
            turn,
            listOf("call_1" to "1. Example"),
        )
        assertEquals("tool", next.last()["role"]!!.jsonPrimitive.content)
        assertEquals("call_1", next.last()["tool_call_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun duckDuckGoKeepsPublicHttpsHits() {
        val html = """
            <a class="result__a" href="https://duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpost&rut=1">Example post</a>
            <a class="result__snippet">A short snippet.</a>
            <a class="result-link" href="http://192.168.1.1/secret">Local</a>
        """.trimIndent()
        val hits = AiTools.parseDuckDuckGo(html)
        assertEquals(1, hits.size)
        assertEquals("https://example.com/post", hits[0].url)
        assertEquals("Example post", hits[0].title)
        assertTrue(AiTools.formatHits(hits).contains("example.com/post"))
        assertEquals("No results.", AiTools.formatHits(emptyList()))
    }

    @Test
    fun duckDuckGoLiteMarkupIsARealSearchResult() {
        val html = """
            <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpost&amp;rut=1" class='result-link'>Example post</a>
            <td class='result-snippet'>A short snippet.</td>
        """.trimIndent()
        val hits = AiTools.parseDuckDuckGo(html)
        assertEquals(1, hits.size)
        assertEquals("https://example.com/post", hits[0].url)
        assertEquals("Example post", hits[0].title)
        assertTrue(hits[0].snippet.contains("short snippet"))
        assertEquals(
            "https://lite.duckduckgo.com/lite/?q=weather+in+Kitchener",
            AiTools.searchUrl("weather in Kitchener"),
        )
    }

    @Test
    fun toolOnlyTurnAtTheCapStillHasToBeAnswered() {
        val call = AiTools.Call("call_1", "web_fetch", "{}")
        assertEquals("run", AiTools.toolStep(listOf(call), 0, tools = true))
        assertEquals("finish", AiTools.toolStep(listOf(call), AiTools.MAX_ROUNDS, tools = true))
        assertEquals("answer", AiTools.toolStep(emptyList(), 1, tools = true))
    }

    @Test
    fun pageFetchRejectsPrivateHosts() {
        assertEquals("https://example.com/a", AiTools.publicPageUrl("https://example.com/a"))
        assertNull(AiTools.publicPageUrl("http://example.com/a"))
        assertNull(AiTools.publicPageUrl("https://user:pw@example.com/a"))
        assertNull(AiTools.publicPageUrl("https://127.0.0.1/latest"))
        assertNull(AiTools.publicPageUrl("https://169.254.169.254/"))
        assertEquals("Hello there", AiTools.stripHtml("<script>alert(1)</script><p>Hello <b>there</b></p>"))
    }

    @Test
    fun calculateUsesThePhoneCalculatorAndRefusesCode() {
        assertEquals("4", AiTools.calculate("2+2"))
        assertEquals("3", AiTools.calculate("sqrt(9)"))
        assertEquals("Could not calculate.", AiTools.calculate("process.exit(1)"))
    }

    @Test
    fun unknownFieldsAreTheOnlyReasonToDropFlags() {
        assertEquals(AiTools.Drop(privacy = true, tools = false), AiTools.droppedFields("Unrecognized request argument supplied: store"))
        assertEquals(AiTools.Drop(privacy = false, tools = true), AiTools.droppedFields("tools is not supported by this model"))
        assertEquals(AiTools.Drop(privacy = false, tools = false), AiTools.droppedFields("No endpoints found that match your data policy (ZDR)"))
        assertFalse(AiTools.droppedFields("invalid api key").privacy)
    }

    @Test
    fun streamAccumulatorKeepsToolArguments() {
        val accum = AiTools.TurnAccum(openai = true)
        accum.acceptData("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"web_search","arguments":""}}]}}]}""")
        accum.acceptData("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"query\":\"x\"}"}}]}}]}""")
        val turn = accum.turn()
        assertEquals("web_search", turn.calls[0].name)
        assertEquals("{\"query\":\"x\"}", turn.calls[0].arguments)
        assertEquals(
            listOf("Searching", "x"),
            AiTools.activity(turn.calls),
        )
    }

    @Test
    fun searchActivityNamesTheQueryThenTheSites() {
        val call = AiTools.Call("call_1", "web_search", """{"query":"weather in Kitchener"}""")
        assertEquals(listOf("Searching", "weather in Kitchener"), AiTools.activity(listOf(call)))
        val hits = listOf(
            AiTools.Hit("Forecast", "https://www.weather.gc.ca/city", "rain"),
            AiTools.Hit("Wiki", "https://en.wikipedia.org/wiki/Kitchener", ""),
        )
        assertEquals(listOf("weather.gc.ca", "en.wikipedia.org"), AiTools.hosts(hits))
        assertEquals(
            listOf("Searching", "weather in Kitchener", "· weather.gc.ca", "· en.wikipedia.org"),
            AiTools.activity(listOf(call), AiTools.hosts(hits)),
        )
        val page = AiTools.Call("call_2", "web_fetch", """{"url":"https://example.com/post"}""")
        assertEquals(listOf("Reading", "example.com"), AiTools.activity(listOf(page)))
    }

    @Test
    fun searchFailureStaysOneShortLine() {
        val html = "<html><body>" + "x".repeat(50_000) + " anomaly detection challenge</body></html>"
        val shown = AiTools.shortFailure(html)
        assertEquals("Search failed.", shown)
        assertFalse(shown.contains("anomaly"))
        assertTrue(shown.length < 80)
        assertFalse(shown.contains('\n'))
        assertEquals("Search failed.", AiTools.chatDisplay("LLM error 502: $html"))
        assertEquals("Could not reach the model.", AiTools.chatDisplay("LLM error 500: " + "{".repeat(500)))
        assertEquals("Hello", AiTools.chatDisplay("Hello"))
        val answer = "Kotlin has a GC. Rust does not.\n\nUse Kotlin on Android."
        assertEquals(answer, AiTools.chatDisplay(answer))
    }

    @Test
    fun searchDoesNotReturnTheErrorPage() {
        val html = "<!DOCTYPE html><html><body>captcha " + "z".repeat(9_000) + "</body></html>"
        val failed = AiTools.perform("web_search", """{"query":"weather"}""") { html }
        assertEquals("Search failed.", failed.text)
        assertTrue(failed.failed)
        assertTrue(failed.sites.isEmpty())
        assertFalse(failed.text.contains("captcha"))
        val ok = """
            <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpost&amp;rut=1" class='result-link'>Example post</a>
            <td class='result-snippet'>A short snippet.</td>
        """.trimIndent()
        val hit = AiTools.perform("web_search", """{"query":"cedar"}""") { ok }
        assertFalse(hit.failed)
        assertEquals(listOf("example.com"), hit.sites)
        assertTrue(hit.text.contains("example.com/post"))
    }
}

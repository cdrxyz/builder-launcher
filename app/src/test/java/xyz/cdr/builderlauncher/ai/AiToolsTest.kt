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
        assertEquals("Searching…", AiTools.status(turn.calls))
    }
}

package xyz.cdr.builderlauncher.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadsTest {
    private val html = """
        <!doctype html>
        <html><head>
        <title>Browser title</title>
        <meta property="og:title" content="The Real Title">
        <meta property="og:site_name" content="The Atlantic">
        </head><body>
        <nav>Home About</nav>
        <article>
        <h1>The Real Title</h1>
        <p>First paragraph of the piece.</p>
        <p>Second paragraph continues the argument.</p>
        <script>var secret = "do not read";</script>
        </article>
        <footer>Copyright 2026</footer>
        </body></html>
    """.trimIndent()

    @Test
    fun hashMatchesTheWebApp() {
        assertEquals("046a4934057f51cf", Reads.fnv1a64("https://example.com/a"))
        assertEquals("r046a4934057f51cf", Reads.articleId("https://example.com/a"))
        assertEquals("r046a4934057f51cf", Reads.articleId("HTTPS://Example.com/a/"))
    }

    @Test
    fun publicUrlRejectsPrivateAndBareNumbers() {
        assertEquals("https://example.com/a", Reads.normalizeUrl("https://example.com/a"))
        assertEquals("https://nytimes.com/story", Reads.normalizeUrl("nytimes.com/story"))
        assertEquals(
            "https://solidstarts.com/articles/paced-bottle-feeding/",
            Reads.fetchUrl("https://solidstarts.com/articles/paced-bottle-feeding/"),
        )
        assertEquals(
            "https://solidstarts.com/articles/paced-bottle-feeding",
            Reads.normalizeUrl("https://solidstarts.com/articles/paced-bottle-feeding/"),
        )
        assertEquals(
            "https://solidstarts.com/articles/paced-bottle-feeding/",
            Reads.nextFetchUrl(
                "https://solidstarts.com/articles/paced-bottle-feeding",
                "/articles/paced-bottle-feeding/",
            ),
        )
        assertEquals(
            Reads.articleId("https://solidstarts.com/articles/paced-bottle-feeding/"),
            Reads.articleId("https://solidstarts.com/articles/paced-bottle-feeding"),
        )
        assertNull(Reads.normalizeUrl("1"))
        assertNull(Reads.normalizeUrl("ftp://example.com/a"))
        assertNull(Reads.normalizeUrl("https://localhost/a"))
        assertNull(Reads.normalizeUrl("https://10.1.2.3/a"))
        assertNull(Reads.normalizeUrl("https://user:pass@example.com/a"))
        assertEquals("nytimes.com", Reads.publicationFromUrl("https://www.nytimes.com/story"))
    }

    @Test
    fun extractTitlePublicationAndBody() {
        val article = ArticleExtract.extract(html, "https://www.theatlantic.com/story")
        assertEquals("The Real Title", article?.title)
        assertEquals("The Atlantic", article?.publication)
        assertTrue(article!!.text.contains("First paragraph of the piece."))
        assertTrue(article.text.contains("Second paragraph continues the argument."))
        assertTrue(!article.text.contains("Home About"))
        assertTrue(!article.text.contains("do not read"))
        assertTrue(!article.text.contains("Copyright"))
    }

    @Test
    fun publicationFallsBackToHost() {
        val bare = """
            <html><head><title>A dispatch from the city desk this morning</title></head>
            <body><article><p>A dispatch from the city desk this morning, with enough words to count as an article.</p></article></body></html>
        """.trimIndent()
        val article = ArticleExtract.extract(bare, "https://www.nytimes.com/2024/story")
        assertEquals("nytimes.com", article?.publication)
        assertEquals("A dispatch from the city desk this morning", article?.title)
    }

    @Test
    fun durationAndIndex() {
        val words = (1..160).joinToString(" ") { "word" }
        assertEquals(60_000L, Reads.durationMs(words))
        assertEquals("0:00 of 1:00", Reads.positionLabel(0, 60_000L))
        assertEquals("12:00 of 45:00", Reads.positionLabel(12 * 60 * 1000L, 45 * 60 * 1000L))
        assertEquals(0, Reads.indexFromInput("1", 3))
        assertEquals(2, Reads.indexFromInput(" 3 ", 3))
        assertNull(Reads.indexFromInput("4", 3))
        assertNull(Reads.indexFromInput("https://example.com", 3))
    }

    @Test
    fun addPutsTheUrlOnTopAndDropsTheOldestPastTheCap() {
        val older = article("old", addedAt = 1)
        val newer = article("new", addedAt = 5)
        val (added, dropped) = Reads.add(listOf(older), newer, now = 9)
        assertEquals(listOf("new", "old"), added.map { it.id })
        assertEquals(9L, added.first().addedAt)
        assertTrue(dropped.isEmpty())

        val many = (1..Reads.MAX_ARTICLES).map { article("a$it", addedAt = it.toLong()) }
        val (capped, gone) = Reads.add(many, article("fresh", addedAt = 1), now = 500)
        assertEquals(Reads.MAX_ARTICLES, capped.size)
        assertEquals("fresh", capped.first().id)
        assertEquals(listOf("a1"), gone)
    }

    @Test
    fun reAddClearsTheDeletedId() {
        assertEquals(listOf("other", "old"), Reads.deletedIdsAfterAdd(listOf("r1", "other"), "r1", listOf("old")))
    }

    @Test
    fun mergeKeepsNewerTextAndLaterPosition() {
        val left = ReadBackup(
            articles = listOf(article("r1", title = "Old", text = "short text that is still long enough to keep", addedAt = 1)),
            progress = listOf(ReadProgress("r1", positionMs = 1000, updatedAt = 5)),
        )
        val right = ReadBackup(
            articles = listOf(article("r1", title = "New", text = "a much longer body that should win because it was added later and has more words", addedAt = 8)),
            progress = listOf(ReadProgress("r1", positionMs = 200, updatedAt = 4)),
            deletedIds = listOf("gone"),
        )
        val merged = Reads.mergeBackup(left, right)
        assertEquals("New", merged.articles.single().title)
        assertTrue(merged.articles.single().text.startsWith("a much longer"))
        assertEquals(1000L, merged.progress.single().positionMs)
        assertEquals(listOf("gone"), merged.deletedIds)
        val dropped = Reads.mergeBackup(merged, ReadBackup(deletedIds = listOf("r1")))
        assertTrue(dropped.articles.isEmpty())
        assertTrue(dropped.progress.isEmpty())
    }

    @Test
    fun chunksCoverTheTextAndSeekIntoTheSecondSentence() {
        val text = "First sentence here. Second sentence follows."
        val chunks = Reads.chunks(text)
        assertEquals(2, chunks.size)
        assertEquals("First sentence here.", text.substring(chunks[0].start, chunks[0].end))
        assertEquals(1, Reads.chunkIndexAt(chunks, chunks[1].startMs))
        assertEquals(15_000L, Reads.skip(0, 60_000L, Reads.SKIP_MS))
    }

    @Test
    fun audioProgressNamesPreparingReadyAndFailed() {
        assertEquals("preparing audio", Reads.audioProgressLabel("preparing", 0, 1))
        assertEquals("audio ready", Reads.audioProgressLabel("ready", 17, 17))
        assertEquals("audio failed · Speech HTTP 429", Reads.audioProgressLabel("failed", 3, 17, "Speech HTTP 429"))
        assertEquals("audio failed", Reads.audioProgressLabel("failed"))
        assertEquals("281fcf45454ff909", Reads.speechHash("Hello there."))
        assertEquals(true, Reads.usesGrokVoice(true, "token"))
        assertEquals(false, Reads.usesGrokVoice(true, " "))
        assertEquals(false, Reads.usesGrokVoice(false, "token"))
        assertEquals(true, Reads.storedAudioReady(listOf(0)))
        assertEquals(false, Reads.storedAudioReady(emptyList()))
        assertEquals(
            "/api/articles/audio?id=r046a4934057f51cf&voice=leo&hash=281fcf45454ff909",
            Reads.storedAudioQuery("r046a4934057f51cf", hash = "281fcf45454ff909"),
        )
        assertEquals(30_000L, Reads.wordMsFromAudio(15_000L, 30_000L, 60_000L))
        assertEquals(15_000L, Reads.audioMsFromWord(30_000L, 30_000L, 60_000L))
        assertEquals(true, Reads.speechSocketUrl().startsWith("wss://api.x.ai/v1/tts?"))
        assertEquals(true, Reads.speechSocketUrl().contains("optimize_streaming_latency=1"))
        val frames = Reads.speechTextFrames("a".repeat(9_000))
        assertEquals("text.done", Reads.speechEvent(frames.last()).first)
        assertEquals(3, frames.count { Reads.speechEvent(it).first == "text.delta" })
        assertEquals("audio.delta", Reads.speechEvent("""{"type":"audio.delta","delta":"YQ=="}""").first)
        assertEquals("YQ==", Reads.speechEvent("""{"type":"audio.delta","delta":"YQ=="}""").second)
        assertEquals(false, Reads.shouldStartSpeech(100))
        assertEquals(true, Reads.shouldStartSpeech(Reads.SPEECH_START_BYTES))
        assertEquals("generating…", Reads.articleDownloadLabel(""))
        assertEquals("generating…", Reads.articleDownloadLabel("preparing audio"))
        assertEquals("generating…", Reads.articleDownloadLabel("preparing audio", 0, 0))
        assertEquals("", Reads.articleDownloadLabel("audio ready"))
        assertEquals("downloading…", Reads.articleDownloadLabel("audio ready", 0, 0))
        assertEquals("downloading…", Reads.articleDownloadLabel("audio ready", 10, 0))
        assertEquals("37%", Reads.articleDownloadLabel("audio ready", 37, 100))
        assertEquals("", Reads.articleDownloadLabel("audio failed"))
        assertEquals("The Real Title.mp3", Reads.articleFileName("The Real Title"))
        assertEquals("article.mp3", Reads.articleFileName(""))
    }

    private fun article(id: String, title: String = id, text: String = "body", addedAt: Long = 0) =
        ReadArticle(id = id, url = "https://example.com/$id", title = title, publication = "Example", text = text, addedAt = addedAt)
}

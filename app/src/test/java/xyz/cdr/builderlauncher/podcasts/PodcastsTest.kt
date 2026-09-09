package xyz.cdr.builderlauncher.podcasts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastsTest {
    @Test
    fun moreCopyMatchesNotesLink() {
        assertEquals("… all podcasts >", Podcasts.MORE)
        assertEquals("<", Podcasts.BACK)
        assertEquals(5L * 1024 * 1024 * 1024, Podcasts.DEFAULT_CACHE_BYTES)
    }

    @Test
    fun queryMatchesPodcastPrefixes() {
        assertTrue(Podcasts.matchesQuery("po"))
        assertTrue(Podcasts.matchesQuery("pod"))
        assertTrue(Podcasts.matchesQuery("podcast"))
        assertTrue(Podcasts.matchesQuery("podcasts"))
        assertTrue(Podcasts.matchesQuery("PODCASTS"))
        assertFalse(Podcasts.matchesQuery(""))
        assertFalse(Podcasts.matchesQuery("p"))
        assertFalse(Podcasts.matchesQuery("hub"))
        assertFalse(Podcasts.matchesQuery("podcastsapp"))
    }

    @Test
    fun looksLikeFeedUrl() {
        assertTrue(Podcasts.looksLikeFeedUrl("https://atp.fm/episodes?format=rss"))
        assertTrue(Podcasts.looksLikeFeedUrl("http://feeds.example.com/show.xml"))
        assertFalse(Podcasts.looksLikeFeedUrl("atp"))
        assertFalse(Podcasts.looksLikeFeedUrl(""))
        assertFalse(Podcasts.looksLikeFeedUrl("ftp://nope"))
    }

    @Test
    fun finishedWhenNearEndOrFlagged() {
        assertFalse(Podcasts.finished(null))
        assertFalse(Podcasts.finished(EpisodeProgress("e", positionMs = 10_000, durationMs = 3_600_000)))
        assertTrue(Podcasts.finished(EpisodeProgress("e", positionMs = 3_580_000, durationMs = 3_600_000)))
        assertTrue(Podcasts.finished(EpisodeProgress("e", positionMs = 95, durationMs = 100)))
        assertTrue(Podcasts.finished(EpisodeProgress("e", positionMs = 10, durationMs = 100, finished = true)))
        assertFalse(Podcasts.finished(EpisodeProgress("e", positionMs = 10, durationMs = 0)))
    }

    @Test
    fun formatsDurationAndCache() {
        assertEquals("1:02:03", Podcasts.formatDuration(3_723_000))
        assertEquals("45:00", Podcasts.formatDuration(45 * 60 * 1000L))
        assertEquals("0:05", Podcasts.formatDuration(5_000))
        assertEquals("12:00 of 45:00", Podcasts.formatPosition(12 * 60 * 1000L, 45 * 60 * 1000L))
        assertEquals("5 GB", Podcasts.cacheLabel(Podcasts.DEFAULT_CACHE_BYTES))
        assertEquals("1 GB", Podcasts.cacheLabel(1L * 1024 * 1024 * 1024))
        assertEquals("512 MB", Podcasts.cacheLabel(512L * 1024 * 1024))
    }

    @Test
    fun parseItunesAndHmsDuration() {
        assertEquals(3_723_000L, Podcasts.parseDuration("1:02:03"))
        assertEquals(125_000L, Podcasts.parseDuration("2:05"))
        assertEquals(90_000L, Podcasts.parseDuration("90"))
        assertEquals(0L, Podcasts.parseDuration(""))
        assertEquals(0L, Podcasts.parseDuration("nope"))
    }

    @Test
    fun homeRowsContinueThenNewThenAlphaShows() {
        val atp = PodcastShow("https://atp.fm/rss", "Accidental Tech Podcast", "Marco")
        val analog = PodcastShow("https://analog.fm/rss", "The Talk Show", "John")
        val zed = PodcastShow("https://zed.fm/rss", "Zed Show", "Z")
        val oldUnfinished = episode("old", atp.feedUrl, "Old ATP", pubDate = 1_000)
        val playing = episode("play", analog.feedUrl, "Playing analog", pubDate = 2_000)
        val playing2 = episode("play2", zed.feedUrl, "Playing zed", pubDate = 3_000)
        val playing3 = episode("play3", atp.feedUrl, "Playing atp 3", pubDate = 4_000)
        val playing4 = episode("play4", atp.feedUrl, "Playing atp 4", pubDate = 5_000)
        val newest = episode("n1", atp.feedUrl, "Newest", pubDate = 9_000)
        val new2 = episode("n2", analog.feedUrl, "New two", pubDate = 8_000)
        val new3 = episode("n3", zed.feedUrl, "New three", pubDate = 7_000)
        val new4 = episode("n4", atp.feedUrl, "New four", pubDate = 6_500)
        val new5 = episode("n5", analog.feedUrl, "New five", pubDate = 6_000)
        val new6 = episode("n6", zed.feedUrl, "New six", pubDate = 5_500)
        val done = episode("done", atp.feedUrl, "Done", pubDate = 20_000)
        val progress = mapOf(
            "old" to EpisodeProgress("old", 60_000, 3_600_000, lastPlayedAt = 100),
            "play" to EpisodeProgress("play", 10_000, 3_600_000, lastPlayedAt = 400),
            "play2" to EpisodeProgress("play2", 10_000, 3_600_000, lastPlayedAt = 300),
            "play3" to EpisodeProgress("play3", 10_000, 3_600_000, lastPlayedAt = 200),
            "play4" to EpisodeProgress("play4", 10_000, 3_600_000, lastPlayedAt = 50),
            "done" to EpisodeProgress("done", 3_600_000, 3_600_000, lastPlayedAt = 999, finished = true),
        )
        val rows = Podcasts.homeRows(
            shows = listOf(zed, analog, atp),
            episodes = listOf(
                oldUnfinished, playing, playing2, playing3, playing4,
                newest, new2, new3, new4, new5, new6, done,
            ),
            progress = progress,
        )
        val continueIds = rows.filterIsInstance<PodcastHomeRow.Continue>().map { it.episode.id }
        assertEquals(listOf("play", "play2", "play3"), continueIds)
        val newIds = rows.filterIsInstance<PodcastHomeRow.Fresh>().map { it.episode.id }
        assertEquals(listOf("n1", "n2", "n3", "n4", "n5"), newIds)
        assertFalse(newIds.contains("done"))
        assertFalse(newIds.contains("play"))
        val showTitles = rows.filterIsInstance<PodcastHomeRow.Subscription>().map { it.show.title }
        assertEquals(listOf("Accidental Tech Podcast", "The Talk Show", "Zed Show"), showTitles)
    }

    @Test
    fun evictsOldestUntilUnderCapKeepingPlaying() {
        val files = listOf(
            PodcastCacheFile("a", 4_000, lastAccessAt = 1),
            PodcastCacheFile("b", 4_000, lastAccessAt = 2),
            PodcastCacheFile("c", 3_000, lastAccessAt = 3),
        )
        assertEquals(listOf("a", "b"), Podcasts.filesToDelete(files, capBytes = 5_000, keepIds = setOf("c")))
        assertEquals(emptyList<String>(), Podcasts.filesToDelete(files, capBytes = 20_000))
        assertEquals(listOf("a"), Podcasts.filesToDelete(files, capBytes = 7_000))
    }

    private fun episode(id: String, showId: String, title: String, pubDate: Long) = PodcastEpisode(
        id = id,
        showId = showId,
        title = title,
        pubDate = pubDate,
        durationMs = 3_600_000,
        enclosureUrl = "https://cdn.example/$id.mp3",
    )
}

class PodcastSearchTest {
    @Test
    fun parsesItunesHits() {
        val raw = """
            {"resultCount":2,"results":[
              {"collectionName":"Accidental Tech Podcast","artistName":"Marco Arment","feedUrl":"https://atp.fm/rss","artworkUrl600":"https://img/atp.jpg","collectionId":123},
              {"artistName":"missing feed"}
            ]}
        """.trimIndent()
        val hits = PodcastSearch.parse(raw)
        assertEquals(1, hits.size)
        assertEquals("Accidental Tech Podcast", hits[0].title)
        assertEquals("Marco Arment", hits[0].author)
        assertEquals("https://atp.fm/rss", hits[0].feedUrl)
        assertEquals("https://img/atp.jpg", hits[0].artworkUrl)
    }

    @Test
    fun searchEmptyOnJunk() {
        assertTrue(PodcastSearch.parse("{}").isEmpty())
        assertTrue(PodcastSearch.parse("not-json").isEmpty())
    }
}

class PodcastRssTest {
    @Test
    fun parsesChannelAndItems() {
        val xml = """
            <?xml version="1.0"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Accidental Tech Podcast</title>
                <itunes:author>Marco Arment</itunes:author>
                <item>
                  <title>Episode 1: Hello</title>
                  <guid>atp-1</guid>
                  <pubDate>Mon, 01 Jan 2024 12:00:00 GMT</pubDate>
                  <enclosure url="https://cdn.example/1.mp3" length="1000" type="audio/mpeg" />
                  <itunes:duration>1:02:03</itunes:duration>
                  <description>First show</description>
                </item>
                <item>
                  <title>No audio</title>
                  <guid>skip</guid>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val feed = PodcastRss.parse(xml, "https://atp.fm/rss")!!
        assertEquals("Accidental Tech Podcast", feed.show.title)
        assertEquals("Marco Arment", feed.show.author)
        assertEquals("https://atp.fm/rss", feed.show.feedUrl)
        assertEquals(1, feed.episodes.size)
        val ep = feed.episodes.single()
        assertEquals("atp-1", ep.id)
        assertEquals("Episode 1: Hello", ep.title)
        assertEquals("https://cdn.example/1.mp3", ep.enclosureUrl)
        assertEquals(3_723_000L, ep.durationMs)
        assertTrue(ep.pubDate > 0)
    }

    @Test
    fun fallsBackToEnclosureAsId() {
        val xml = """
            <rss><channel><title>X</title>
            <item>
              <title>One</title>
              <enclosure url="https://cdn.example/one.mp3" type="audio/mpeg" />
            </item>
            </channel></rss>
        """.trimIndent()
        val ep = PodcastRss.parse(xml, "https://x.fm/rss")!!.episodes.single()
        assertEquals("https://cdn.example/one.mp3", ep.id)
    }

    @Test
    fun nullOnJunk() {
        assertEquals(null, PodcastRss.parse("not xml", "https://x"))
        assertEquals(null, PodcastRss.parse("<rss></rss>", "https://x"))
    }
}

class PodcastOpmlTest {
    @Test
    fun parsesOvercastExport() {
        val opml = """
            <?xml version="1.0" encoding="utf-8"?>
            <opml version="1.0">
              <head><title>Overcast Subscriptions</title></head>
              <body>
                <outline text="feeds">
                  <outline type="rss" text="Accidental Tech Podcast" xmlUrl="https://atp.fm/episodes?format=rss" htmlUrl="https://atp.fm/" />
                  <outline type="rss" title="The Talk Show" xmlUrl="https://daringfireball.net/thetalkshow/rss" />
                  <outline text="not a feed" />
                </outline>
              </body>
            </opml>
        """.trimIndent()
        val hits = PodcastOpml.parse(opml)
        assertEquals(
            listOf("https://atp.fm/episodes?format=rss", "https://daringfireball.net/thetalkshow/rss"),
            hits.map { it.feedUrl },
        )
        assertEquals("Accidental Tech Podcast", hits[0].title)
        assertEquals("The Talk Show", hits[1].title)
        assertTrue(PodcastOpml.looksLike(opml))
        assertFalse(PodcastOpml.looksLike("AAPL,Apple"))
        assertFalse(PodcastOpml.looksLike("https://atp.fm/rss"))
    }
}

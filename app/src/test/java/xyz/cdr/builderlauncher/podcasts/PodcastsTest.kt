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
        assertEquals("0 MB", Podcasts.cacheLabel(0))
        assertEquals("0 MB", Podcasts.cacheLabel(512 * 1024L))
        assertEquals("1.5 MB", Podcasts.cacheLabel((1536L * 1024)))
        assertEquals("1.2 GB", Podcasts.cacheLabel((12L * 1024 * 1024 * 1024) / 10))
        assertFalse(Podcasts.cacheLabel(0).endsWith(" B"))
        assertFalse(Podcasts.cacheLabel(100).contains(" B"))
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
        val headers = rows.filterIsInstance<PodcastHomeRow.Header>().map { it.title }
        assertEquals(
            listOf(Podcasts.SECTION_RECENT, Podcasts.SECTION_NEXT, Podcasts.SECTION_SHOWS),
            headers,
        )
        assertTrue(rows[0] is PodcastHomeRow.Header)
        assertEquals(Podcasts.SECTION_RECENT, (rows[0] as PodcastHomeRow.Header).title)
        val withoutCurrent = Podcasts.homeRows(
            shows = listOf(zed, analog, atp),
            episodes = listOf(
                oldUnfinished, playing, playing2, playing3, playing4,
                newest, new2, new3, new4, new5, new6, done,
            ),
            progress = progress,
            currentEpisodeId = "play",
        )
        assertEquals(
            listOf("play2", "play3", "old"),
            withoutCurrent.filterIsInstance<PodcastHomeRow.Continue>().map { it.episode.id },
        )
        assertFalse(withoutCurrent.filterIsInstance<PodcastHomeRow.Fresh>().any { it.episode.id == "play" })
        val skippedRows = Podcasts.homeRows(
            shows = listOf(zed, analog, atp),
            episodes = listOf(
                oldUnfinished, playing, playing2, playing3, playing4,
                newest, new2, new3, new4, new5, new6, done,
            ),
            progress = progress + ("n1" to EpisodeProgress("n1", skipped = true)),
        )
        assertFalse(skippedRows.filterIsInstance<PodcastHomeRow.Fresh>().any { it.episode.id == "n1" })
        assertEquals("n2", skippedRows.filterIsInstance<PodcastHomeRow.Fresh>().first().episode.id)
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

    @Test
    fun skipClampsToEpisode() {
        assertEquals(15_000L, Podcasts.SKIP_MS)
        assertEquals(25_000L, Podcasts.skip(10_000, 60_000, Podcasts.SKIP_MS))
        assertEquals(0L, Podcasts.skip(10_000, 60_000, -Podcasts.SKIP_MS))
        assertEquals(60_000L, Podcasts.skip(50_000, 60_000, Podcasts.SKIP_MS))
        assertEquals(0L, Podcasts.skip(0, 0, Podcasts.SKIP_MS))
    }

    @Test
    fun speedStepsFromPointEightToThree() {
        assertEquals(
            listOf(0.8f, 1.0f, 1.1f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f, 2.5f, 3.0f),
            Podcasts.SPEED_STEPS,
        )
        assertEquals(0.8f, Podcasts.snapSpeed(0.5f), 0.001f)
        assertEquals(1.2f, Podcasts.snapSpeed(1.19f), 0.001f)
        assertEquals(3.0f, Podcasts.snapSpeed(9f))
        assertEquals("0.8×", Podcasts.formatSpeed(0.8f))
        assertEquals("1×", Podcasts.formatSpeed(1.0f))
        assertEquals("1.1×", Podcasts.formatSpeed(1.1f))
        assertEquals("1.4×", Podcasts.formatSpeed(1.4f))
        assertEquals("2×", Podcasts.formatSpeed(2.0f))
        assertEquals("2.5×", Podcasts.formatSpeed(2.5f))
        assertEquals(0.8f, Podcasts.speedAt(0f, 100f))
        assertEquals(3.0f, Podcasts.speedAt(100f, 100f))
        assertEquals(0f, Podcasts.speedProgress(0.8f), 0.001f)
        assertEquals(1f, Podcasts.speedProgress(3.0f), 0.001f)
        assertEquals(1.0f, Podcasts.DEFAULT_SPEED)
    }

    @Test
    fun pollSkipsSubSecondPositionTicks() {
        val base = PlaybackState("e", true, 1_200L, 60_000L, 1.4f)
        assertFalse(Podcasts.shouldPublishPlayback(base, base.copy(positionMs = 1_800L)))
        assertTrue(Podcasts.shouldPublishPlayback(base, base.copy(positionMs = 2_000L)))
        assertTrue(Podcasts.shouldPublishPlayback(base, base.copy(playing = false)))
        assertTrue(Podcasts.shouldPublishPlayback(base, base.copy(speed = 1.0f)))
        assertTrue(Podcasts.shouldPublishPlayback(base, base.copy(episodeId = "f")))
        assertEquals(1_000L, Podcasts.POSITION_PUBLISH_MS)
    }

    @Test
    fun scrubMapsXToPosition() {
        assertEquals(0L, Podcasts.progressAt(0f, 100f, 60_000))
        assertEquals(30_000L, Podcasts.progressAt(50f, 100f, 60_000))
        assertEquals(60_000L, Podcasts.progressAt(100f, 100f, 60_000))
        assertEquals(0L, Podcasts.progressAt(50f, 0f, 60_000))
        assertEquals(0.5f, Podcasts.fraction(30_000, 60_000), 0.001f)
        assertEquals(0f, Podcasts.fraction(10, 0))
        assertTrue(Podcasts.nowPlayingVisible(playing = true, episodeId = "e"))
        assertFalse(Podcasts.nowPlayingVisible(playing = false, episodeId = "e"))
        assertFalse(Podcasts.nowPlayingVisible(playing = true, episodeId = null))
        assertTrue(Podcasts.nowPlayingBarVisible(episodeId = "e"))
        assertFalse(Podcasts.nowPlayingBarVisible(episodeId = null))
        assertFalse(Podcasts.nowPlayingBarVisible(episodeId = ""))
        assertFalse(Podcasts.nowPlayingBarVisible(episodeId = "e", finished = true))
        assertFalse(Podcasts.playbackEnded(playing = true, positionMs = 3_580_000, durationMs = 3_600_000))
        assertTrue(Podcasts.playbackEnded(playing = false, positionMs = 3_580_000, durationMs = 3_600_000))
        assertEquals(
            HomePodcastMark.HEADPHONES,
            Podcasts.homePodcastMark(playing = false, episodeLoaded = false, pausedForMs = null),
        )
        assertEquals(
            HomePodcastMark.PAUSE,
            Podcasts.homePodcastMark(playing = true, episodeLoaded = true, pausedForMs = null),
        )
        assertEquals(
            HomePodcastMark.PLAY,
            Podcasts.homePodcastMark(playing = false, episodeLoaded = true, pausedForMs = 1_000L),
        )
        assertEquals(
            HomePodcastMark.HEADPHONES,
            Podcasts.homePodcastMark(playing = false, episodeLoaded = true, pausedForMs = Podcasts.HOME_MARK_IDLE_MS),
        )
        assertEquals(8_000L, Podcasts.HOME_MARK_IDLE_MS)
        assertEquals("12:00 of 45:00", Podcasts.episodeLeftMeta(EpisodeProgress("e", 12 * 60 * 1000L, 45 * 60 * 1000L, lastPlayedAt = 1), 45 * 60 * 1000L))
        assertEquals("45:00", Podcasts.episodeLeftMeta(null, 45 * 60 * 1000L))
        assertEquals("45:00", Podcasts.episodeLeftMeta(EpisodeProgress("e", 0, 45 * 60 * 1000L, skipped = true), 45 * 60 * 1000L))
        assertTrue(Podcasts.skipped(EpisodeProgress("e", skipped = true)))
        assertFalse(Podcasts.skipped(null))
    }

    @Test
    fun scrubAndSpeedBarsInsetFromScreenEdges() {
        assertEquals(36, Podcasts.BAR_SIDE_DP)
        assertTrue(Podcasts.BAR_SIDE_DP > 20)
    }

    @Test
    fun sectionCopy() {
        assertEquals("recent", Podcasts.SECTION_RECENT)
        assertEquals("next 5 episodes", Podcasts.SECTION_NEXT)
        assertEquals("podcasts", Podcasts.SECTION_SHOWS)
    }

    @Test
    fun downloadPercentAndLabel() {
        assertEquals(0, Podcasts.downloadPercent(0, 0))
        assertEquals(0, Podcasts.downloadPercent(10, 0))
        assertEquals(37, Podcasts.downloadPercent(37, 100))
        assertEquals(100, Podcasts.downloadPercent(150, 100))
        assertEquals("download", Podcasts.downloadLabel(downloaded = false, busy = false, percent = 0, knownTotal = false))
        assertEquals("downloaded", Podcasts.downloadLabel(downloaded = true, busy = false, percent = 100, knownTotal = true))
        assertEquals("downloading…", Podcasts.downloadLabel(downloaded = false, busy = true, percent = 0, knownTotal = false))
        assertEquals("downloading 37%", Podcasts.downloadLabel(downloaded = false, busy = true, percent = 37, knownTotal = true))
    }

    @Test
    fun deletesPlayedDownloadsAndKeepsUnfinished() {
        val progress = mapOf(
            "done" to EpisodeProgress("done", 3_600_000, 3_600_000, finished = true),
            "play" to EpisodeProgress("play", 10_000, 3_600_000, lastPlayedAt = 1),
        )
        assertEquals(
            listOf("done"),
            Podcasts.playedDownloadsToDelete(setOf("done", "play", "fresh"), progress),
        )
        assertEquals(emptyList<String>(), Podcasts.playedDownloadsToDelete(setOf("play"), progress))
    }

    @Test
    fun lockScreenKeepsSessionWhilePaused() {
        assertTrue(Podcasts.mediaSessionActive(episodeId = "e", stopped = false))
        assertTrue(Podcasts.mediaSessionActive(episodeId = "e", stopped = false, playing = false))
        assertFalse(Podcasts.mediaSessionActive(episodeId = null, stopped = false, playing = true))
        assertFalse(Podcasts.mediaSessionActive(episodeId = "e", stopped = true))
        assertEquals(
            Podcasts.MEDIA_ACTION_PLAY or Podcasts.MEDIA_ACTION_PLAY_PAUSE or
                Podcasts.MEDIA_ACTION_STOP or Podcasts.MEDIA_ACTION_SEEK or
                Podcasts.MEDIA_ACTION_REWIND or Podcasts.MEDIA_ACTION_FAST_FORWARD,
            Podcasts.mediaActions(playing = false),
        )
        assertEquals(
            Podcasts.MEDIA_ACTION_PAUSE or Podcasts.MEDIA_ACTION_PLAY_PAUSE or
                Podcasts.MEDIA_ACTION_STOP or Podcasts.MEDIA_ACTION_SEEK or
                Podcasts.MEDIA_ACTION_REWIND or Podcasts.MEDIA_ACTION_FAST_FORWARD,
            Podcasts.mediaActions(playing = true),
        )
    }

    @Test
    fun searchRowShowsArtWhenUrlPresent() {
        assertTrue(Podcasts.searchRowShowsArt("https://img/atp.jpg"))
        assertFalse(Podcasts.searchRowShowsArt(""))
        assertFalse(Podcasts.searchRowShowsArt("   "))
    }

    @Test
    fun sortsShowEpisodesNewestOrOldest() {
        val feed = "https://atp.fm/rss"
        val eps = listOf(
            episode("a", feed, "First", pubDate = 1_000),
            episode("b", feed, "Middle", pubDate = 2_000),
            episode("c", feed, "Latest", pubDate = 3_000),
        )
        assertEquals(
            listOf("c", "b", "a"),
            Podcasts.sortEpisodes(eps, EpisodeOrder.NEWEST).map { it.id },
        )
        assertEquals(
            listOf("a", "b", "c"),
            Podcasts.sortEpisodes(eps, EpisodeOrder.OLDEST).map { it.id },
        )
        assertEquals(EpisodeOrder.NEWEST, PodcastShow(feed, "ATP").episodeOrder)
        assertEquals("newest first", Podcasts.episodeOrderLabel(EpisodeOrder.NEWEST))
        assertEquals("oldest first", Podcasts.episodeOrderLabel(EpisodeOrder.OLDEST))
        assertEquals(EpisodeOrder.NEWEST, Podcasts.parseEpisodeOrder(null))
        assertEquals(EpisodeOrder.OLDEST, Podcasts.parseEpisodeOrder("oldest"))
        assertEquals(EpisodeOrder.NEWEST, Podcasts.parseEpisodeOrder("newest"))
        assertEquals(
            EpisodeOrder.OLDEST,
            Podcasts.mergeShow(
                PodcastShow(feed, "ATP", episodeOrder = EpisodeOrder.OLDEST),
                PodcastShow(feed, "Accidental Tech Podcast", author = "Marco"),
            ).episodeOrder,
        )
        assertEquals(
            "Accidental Tech Podcast",
            Podcasts.mergeShow(
                PodcastShow(feed, "ATP", episodeOrder = EpisodeOrder.OLDEST),
                PodcastShow(feed, "Accidental Tech Podcast", author = "Marco"),
            ).title,
        )
    }

    @Test
    fun homeEpisodeTitlesCapAtThreeLines() {
        assertEquals(3, Podcasts.TITLE_LINES)
        assertEquals(3, Podcasts.titleMaxLines(home = true))
        assertEquals(Int.MAX_VALUE, Podcasts.titleMaxLines(home = false))
        assertEquals(1, Podcasts.SHOW_LINES)
        assertEquals(1, Podcasts.showMaxLines(nextEpisodes = true))
        assertEquals(Int.MAX_VALUE, Podcasts.showMaxLines(nextEpisodes = false))
    }

    @Test
    fun formatsEpisodeDate() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        assertEquals("", Podcasts.formatEpisodeDate(0L, timeZone = utc))
        assertEquals(
            "1 Jan 2024",
            Podcasts.formatEpisodeDate(1_704_110_400_000L, locale = java.util.Locale.US, timeZone = utc),
        )
    }

    @Test
    fun stripsHtmlShowNotes() {
        val html = "<p>Hello<br/>world</p><p>More &amp; more</p>"
        assertEquals("Hello\nworld\n\nMore & more", Podcasts.plainNotes(html))
        assertEquals("plain", Podcasts.plainNotes("plain"))
        assertEquals("", Podcasts.plainNotes("   "))
        assertEquals(
            "encoded wins",
            Podcasts.pickNotes(encoded = "<p>encoded wins</p>", summary = "sum", description = "desc"),
        )
        assertEquals("sum", Podcasts.pickNotes(encoded = "", summary = "sum", description = "desc"))
        assertEquals("desc", Podcasts.pickNotes(encoded = "", summary = "", description = "desc"))
    }

    @Test
    fun parsesLinkableTimestamps() {
        val notes = "0:00 Intro\n12:34 Mid\n1:02:03 End\n[4:05] Bracket\n(0:45) Paren"
        val hits = Podcasts.timestamps(notes)
        assertEquals(listOf(0L, 754_000L, 3_723_000L, 245_000L, 45_000L), hits.map { it.positionMs })
        assertEquals("0:00", hits[0].raw)
        assertEquals("1:02:03", hits[2].raw)
        assertEquals(0L, Podcasts.timestampAt(notes, 0))
        assertEquals(754_000L, Podcasts.timestampAt(notes, notes.indexOf("12:34")))
        assertEquals(null, Podcasts.timestampAt(notes, notes.indexOf("Intro")))
        assertEquals(3_723_000L, Podcasts.parseTimestamp("1:02:03"))
        assertEquals(null, Podcasts.parseTimestamp("2024"))
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
        assertEquals("First show", ep.description)
    }

    @Test
    fun prefersContentEncodedShowNotes() {
        val xml = """
            <rss><channel><title>X</title>
            <item>
              <title>One</title>
              <enclosure url="https://cdn.example/one.mp3" type="audio/mpeg" />
              <description><![CDATA[<p>short</p>]]></description>
              <itunes:summary>summary</itunes:summary>
              <content:encoded><![CDATA[<p>0:00 Intro</p><p>12:34 Deep cut</p>]]></content:encoded>
            </item>
            </channel></rss>
        """.trimIndent()
        val ep = PodcastRss.parse(xml, "https://x.fm/rss")!!.episodes.single()
        assertEquals("0:00 Intro\n\n12:34 Deep cut", ep.description)
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

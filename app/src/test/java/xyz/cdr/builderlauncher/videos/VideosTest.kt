package xyz.cdr.builderlauncher.videos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideosTest {
    @Test
    fun aYoutubeLinkBecomesAnId() {
        assertEquals("dQw4w9WgXcQ", Videos.youtubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", Videos.youtubeId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", Videos.youtubeId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("", Videos.youtubeId("not a video"))
    }

    @Test
    fun aPlayerResponseKeepsAudioSeparateFromThePicture() {
        val picked = Videos.pick(
            """
            {"playabilityStatus":{"status":"OK"},
             "videoDetails":{"videoId":"dQw4w9WgXcQ","title":"Song","author":"Artist","lengthSeconds":"213"},
             "streamingData":{"formats":[{"mimeType":"video/mp4; codecs=\"avc1, mp4a.40.2\"","bitrate":400000,"url":"https://example.test/muxed"}],
             "adaptiveFormats":[
               {"mimeType":"audio/webm; codecs=\"opus\"","bitrate":160000,"url":"https://example.test/opus"},
               {"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":128000,"url":"https://example.test/audio"},
               {"mimeType":"video/mp4; codecs=\"avc1\"","bitrate":1000000,"url":"https://example.test/720"}
             ]}}
            """.trimIndent(),
        )
        assertEquals("https://example.test/audio", picked?.audioUrl)
        assertEquals("https://example.test/muxed", picked?.videoUrl)
        assertTrue(picked?.muxed == true)
        assertEquals(213000L, picked?.durationMs)
        assertEquals(null, Videos.pick("""{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in"}}"""))
        val attempts = Videos.playerAttempts("dQw4w9WgXcQ")
        assertEquals(2, attempts.size)
        assertTrue(attempts[0].second.contains("ANDROID"))
        assertTrue(attempts[1].second.contains("IOS"))
    }

    @Test
    fun aProofRequestCarriesTheTokenAndNotASignIn() {
        val page = """
            "visitorData":"Cgtabc","INNERTUBE_CLIENT_VERSION":"2.1"
            \x22bgChallenge\x22:\x7b\x22interpreterUrl\x22:\x7b\x22privateDoNotAccessOrElseTrustedResourceUrlWrappedValue\x22:\x22\/\/www.google.com\/js\/th\/abc.js\x22\x7d,\x22program\x22:\x22prog\x22,\x22globalName\x22:\x22trayride\x22
        """.trimIndent()
        val challenge = VideoProofs.parse(page)
        assertEquals("Cgtabc", challenge?.visitorData)
        assertEquals("2.1", challenge?.clientVersion)
        assertEquals("https://www.google.com/js/th/abc.js", challenge?.interpreterUrl)
        assertEquals("prog", challenge?.program)
        assertEquals("trayride", challenge?.globalName)
        val proof = VideoProof("Cgtabc", "2.1", "player", "stream")
        val body = VideoProofs.webPlayerBody("dQw4w9WgXcQ", proof)
        assertTrue(body.contains("\"poToken\":\"player\""))
        assertTrue(body.contains("visitorData"))
        assertFalse(body.contains("sign in"))
        assertEquals(
            "https://rr.googlevideo.com/videoplayback?id=1&pot=stream",
            VideoProofs.withStreamProof("https://rr.googlevideo.com/videoplayback?id=1", "stream"),
        )
    }

    @Test
    fun savingAVideoReplacesTheSameId() {
        val first = Videos.add(emptyList(), SavedVideo("vdQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "Old"), 1)
        val next = Videos.add(first, SavedVideo("vdQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "New"), 2)
        assertEquals(1, next.size)
        assertEquals("New", next.single().title)
        val merged = Videos.mergeBackup(
            VideoBackup(videos = listOf(next.single()), deletedIds = listOf("gone")),
            VideoBackup(deletedIds = listOf("vdQw4w9WgXcQ")),
            restoredIds = setOf("vdQw4w9WgXcQ"),
        )
        assertFalse(merged.videos.isEmpty())
        assertFalse(merged.deletedIds.contains("vdQw4w9WgXcQ"))
    }
}

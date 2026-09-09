package xyz.cdr.builderlauncher.podcasts

object PodcastOpml {
    fun looksLike(raw: String): Boolean {
        val t = raw.trim().lowercase()
        if (t.isEmpty()) return false
        return t.contains("<opml") && (t.contains("xmlurl") || t.contains("type=\"rss\""))
    }

    fun parse(raw: String): List<PodcastHit> {
        val doc = PodcastRss.document(raw) ?: return emptyList()
        val outlines = doc.getElementsByTagName("*")
        val hits = mutableListOf<PodcastHit>()
        val seen = mutableSetOf<String>()
        for (i in 0 until outlines.length) {
            val n = outlines.item(i)
            if (n !is org.w3c.dom.Element) continue
            val url = n.getAttribute("xmlUrl").ifBlank { n.getAttribute("xmlurl") }.trim()
            if (url.isEmpty()) continue
            if (!Podcasts.looksLikeFeedUrl(url)) continue
            if (!seen.add(url)) continue
            val title = n.getAttribute("text").ifBlank { n.getAttribute("title") }.trim().ifBlank { url }
            hits += PodcastHit(title = title, feedUrl = url)
        }
        return hits
    }
}

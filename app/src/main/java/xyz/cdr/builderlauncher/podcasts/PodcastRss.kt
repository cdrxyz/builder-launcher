package xyz.cdr.builderlauncher.podcasts

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader

object PodcastRss {
    fun parse(xml: String, feedUrl: String): PodcastFeed? {
        val doc = document(xml) ?: return null
        val channel = first(doc.documentElement, "channel") ?: return null
        val title = text(channel, "title").ifBlank { return null }
        val author = text(channel, "itunes:author").ifBlank { text(channel, "author") }
        val artwork = attr(first(channel, "itunes:image"), "href").ifBlank {
            attr(first(first(channel, "image"), "url")?.let { it } ?: first(channel, "image"), "href")
        }.ifBlank { text(first(channel, "image"), "url") }
        val show = PodcastShow(
            feedUrl = feedUrl,
            title = title,
            author = author,
            artworkUrl = artwork,
        )
        val items = children(channel, "item").mapNotNull { item ->
            val enclosure = first(item, "enclosure")
            val url = attr(enclosure, "url").ifBlank { text(item, "link") }
            if (url.isBlank()) return@mapNotNull null
            val guid = text(item, "guid").ifBlank { url }
            PodcastEpisode(
                id = guid,
                showId = feedUrl,
                title = text(item, "title").ifBlank { "untitled" },
                pubDate = parsePubDate(text(item, "pubDate")),
                durationMs = Podcasts.parseDuration(text(item, "itunes:duration")),
                enclosureUrl = url,
                description = Podcasts.pickNotes(
                    encoded = text(item, "content:encoded"),
                    summary = text(item, "itunes:summary").ifBlank { text(item, "summary") },
                    description = text(item, "description"),
                ),
            )
        }
        if (items.isEmpty() && title.isBlank()) return null
        if (items.isEmpty() && children(channel, "item").isEmpty()) {
            // channel with no items is still a valid empty show
        }
        return PodcastFeed(show, items)
    }

    internal fun document(xml: String) = runCatching {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isExpandEntityReferences = false
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        builder.parse(InputSource(StringReader(xml)))
    }.getOrNull()

    internal fun first(parent: Node?, name: String): Element? {
        if (parent == null) return null
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n is Element && local(n) == localName(name)) return n
        }
        val kidsDeep = if (parent is Element) parent.getElementsByTagName("*") else return null
        for (i in 0 until kidsDeep.length) {
            val n = kidsDeep.item(i)
            if (n is Element && local(n) == localName(name) && n.parentNode == parent) return n
        }
        return null
    }

    internal fun children(parent: Node, name: String): List<Element> {
        val out = mutableListOf<Element>()
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n is Element && local(n) == localName(name)) out += n
        }
        return out
    }

    internal fun text(parent: Node?, name: String): String {
        val el = first(parent, name) ?: return ""
        return el.textContent.trim()
    }

    internal fun text(el: Element?): String = el?.textContent?.trim().orEmpty()

    internal fun attr(el: Element?, name: String): String = el?.getAttribute(name)?.trim().orEmpty()

    private fun local(el: Element): String {
        val tag = el.tagName
        return localName(tag)
    }

    private fun localName(name: String): String {
        val i = name.indexOf(':')
        return if (i >= 0) name.substring(i + 1).lowercase() else name.lowercase()
    }

    internal fun parsePubDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "dd MMM yyyy HH:mm:ss Z",
        )
        for (pattern in patterns) {
            val fmt = SimpleDateFormat(pattern, Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("GMT")
            runCatching { fmt.parse(raw)?.time }.getOrNull()?.let { return it }
        }
        return 0L
    }
}

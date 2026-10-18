package xyz.cdr.builderlauncher.read

object ArticleExtract {
    data class Article(val title: String, val publication: String, val text: String)

    private val META = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val DROP = Regex(
        """<(script|style|nav|header|footer|aside|form|noscript|svg|iframe|button)\b[^>]*>[\s\S]*?</\1>""",
        RegexOption.IGNORE_CASE,
    )
    private val BR = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val BLOCK = Regex("""</(p|div|h[1-6]|li|blockquote|tr|section|article|figcaption)>""", RegexOption.IGNORE_CASE)
    private val TAGS = Regex("""<[^>]+>""")

    fun extract(html: String, pageUrl: String): Article? {
        val title = meta(html, "og:title")
            ?: meta(html, "twitter:title")
            ?: elementText(html, "title")
            ?: ""
        val publication = meta(html, "og:site_name")
            ?: meta(html, "application-name")
            ?: meta(html, "publisher")
            ?: Reads.publicationFromUrl(pageUrl)
        val region = elementInner(html, "article")
            ?: elementInner(html, "main")
            ?: elementInner(html, "body")
            ?: html
        val text = plain(region).take(Reads.MAX_TEXT)
        if (text.length < 40) return null
        return Article(
            title = title.ifBlank { text.lineSequence().first() }.trim().take(180),
            publication = publication.ifBlank { Reads.publicationFromUrl(pageUrl) },
            text = text,
        )
    }

    private fun meta(html: String, key: String): String? {
        for (match in META.findAll(html)) {
            val tag = match.value
            val name = attr(tag, "property") ?: attr(tag, "name") ?: continue
            if (!name.equals(key, ignoreCase = true)) continue
            val content = attr(tag, "content")?.let(::decode)?.trim().orEmpty()
            if (content.isNotEmpty()) return content
        }
        return null
    }

    private fun elementText(html: String, tag: String): String? =
        elementInner(html, tag)?.let(::plain)?.ifBlank { null }

    private fun elementInner(html: String, tag: String): String? {
        val re = Regex("""<$tag\b[^>]*>([\s\S]*?)</$tag>""", RegexOption.IGNORE_CASE)
        return re.find(html)?.groupValues?.get(1)
    }

    private fun attr(tag: String, name: String): String? {
        val re = Regex("""\b${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
        val match = re.find(tag) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
    }

    private fun plain(html: String): String {
        var body = DROP.replace(html, " ")
        body = BR.replace(body, "\n")
        body = BLOCK.replace(body, "\n")
        body = TAGS.replace(body, " ")
        return decode(body)
            .lines()
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    internal fun decode(raw: String): String {
        val named = raw
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        return Regex("""&#(x?[0-9a-fA-F]+);""").replace(named) { match ->
            val token = match.groupValues[1]
            val code = if (token.startsWith("x", ignoreCase = true)) {
                token.drop(1).toIntOrNull(16)
            } else {
                token.toIntOrNull()
            }
            if (code == null || code !in 1..0x10FFFF) match.value else Character.toString(code)
        }
    }
}

package com.martin.showfavicon

import java.net.URI

/**
 * Finds favicon candidates in a page's `<head>`.
 *
 * Read-only and without an HTML parser dependency: the tags we look for are
 * simple attribute lists, and a page that hides one in a comment only costs a
 * wasted download before the `/favicon.ico` fallback takes over.
 */
object FaviconResolver {

    private val LINK_TAG = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val REL_ATTRIBUTE = Regex("""\brel\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val HREF_ATTRIBUTE = Regex("""\bhref\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val TYPE_ATTRIBUTE = Regex("""\btype\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

    /**
     * Candidate URLs for [html], best first, with [baseUrl] resolving relative
     * hrefs. Vector icons are skipped because BitmapFactory cannot decode SVG,
     * and the site's `/favicon.ico` is always the last resort.
     */
    fun candidates(html: String, baseUrl: String): List<String> {
        val declared = LINK_TAG.findAll(html)
            .mapNotNull { match -> candidateFrom(match.value, baseUrl) }
            .toList()

        val ranked = declared.sortedBy { it.first }.map { it.second }.distinct()
        val fallback = origin(baseUrl)?.let { "$it/favicon.ico" }
        return (ranked + listOfNotNull(fallback)).distinct()
    }

    /** Returns the ranking and the absolute URL of one `<link>` tag. */
    private fun candidateFrom(tag: String, baseUrl: String): Pair<Int, String>? {
        val rel = REL_ATTRIBUTE.find(tag)?.groupValues?.get(1)?.lowercase().orEmpty()
        if (!rel.contains("icon")) return null

        val href = HREF_ATTRIBUTE.find(tag)?.groupValues?.get(1).orEmpty()
        if (href.isEmpty() || href.startsWith("data:")) return null

        val type = TYPE_ATTRIBUTE.find(tag)?.groupValues?.get(1)?.lowercase().orEmpty()
        if (type.contains("svg") || href.substringBefore('?').endsWith(".svg", ignoreCase = true)) {
            return null
        }

        val absolute = resolve(baseUrl, href) ?: return null
        return rank(rel) to absolute
    }

    /**
     * Apple touch icons are usually the largest PNG a site ships, a `mask-icon`
     * is monochrome artwork, everything else is a plain icon declaration.
     */
    private fun rank(rel: String): Int = when {
        rel.contains("apple-touch-icon") -> 0
        rel.contains("mask-icon") -> 2
        else -> 1
    }

    /** `scheme://host[:port]` of a URL, without path or query. */
    private fun origin(url: String): String? = runCatching {
        val uri = URI(url)
        val host = uri.host ?: return null
        URI(uri.scheme, null, host, uri.port, null, null, null).toString()
    }.getOrNull()

    private fun resolve(baseUrl: String, href: String): String? = runCatching {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            href
        } else {
            URI(baseUrl).resolve(href).toString()
        }
    }.getOrNull()
}

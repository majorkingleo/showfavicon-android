package com.martin.showfavicon

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
     * hrefs. A vector icon ranks below a raster one because it has to be
     * rasterised before it can be cached, and the site's `/favicon.ico` is always
     * the last resort.
     */
    fun candidates(html: String, baseUrl: String): List<String> {
        val declared = LINK_TAG.findAll(html)
            .mapNotNull { match -> candidateFrom(match.value, baseUrl) }
            .toList()

        val ranked = declared.sortedBy { it.first }.map { it.second }.distinct()
        val fallback = Urls.origin(baseUrl)?.let { "$it/favicon.ico" }
        return (ranked + listOfNotNull(fallback)).distinct()
    }

    /** Returns the ranking and the absolute URL of one `<link>` tag. */
    private fun candidateFrom(tag: String, baseUrl: String): Pair<Int, String>? {
        val rel = REL_ATTRIBUTE.find(tag)?.groupValues?.get(1)?.lowercase().orEmpty()
        if (!rel.contains("icon")) return null

        val href = HREF_ATTRIBUTE.find(tag)?.groupValues?.get(1).orEmpty()
        if (href.isEmpty() || href.startsWith("data:")) return null

        val type = TYPE_ATTRIBUTE.find(tag)?.groupValues?.get(1)?.lowercase().orEmpty()
        val vector = type.contains("svg") ||
            href.substringBefore('?').endsWith(".svg", ignoreCase = true)

        val absolute = Urls.resolve(baseUrl, href) ?: return null
        return rank(rel, vector) to absolute
    }

    /**
     * Apple touch icons are usually the largest PNG a site ships, a `mask-icon`
     * is monochrome artwork, and a vector is a vector.
     */
    private fun rank(rel: String, vector: Boolean): Int = when {
        rel.contains("apple-touch-icon") -> 0
        rel.contains("mask-icon") -> 3
        vector -> 2
        else -> 1
    }
}

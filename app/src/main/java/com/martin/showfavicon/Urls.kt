package com.martin.showfavicon

import androidx.core.net.toUri
import java.net.URI

/**
 * URL helpers shared by the settings screen and the fetch worker.
 *
 * Everything here is a pure string operation, so it is cheap enough to run
 * inside a dialog validator.
 */
object Urls {

    /** Adds `https://` when the user typed a bare host and drops the trailing slash. */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = if (trimmed.startsWith(HTTP) || trimmed.startsWith(HTTPS)) {
            trimmed
        } else {
            HTTPS + trimmed
        }
        return withScheme.trimEnd('/')
    }

    /** Host of a URL, empty when it cannot be parsed. */
    fun host(url: String): String = url.toUri().host.orEmpty()

    /** True when the URL has a host, so `https:///path` is rejected. */
    fun isValid(url: String): Boolean = host(normalize(url)).isNotEmpty()

    /** What the settings list shows: the host without its `www.` prefix. */
    fun displayName(url: String): String = host(url).removePrefix("www.")

    /**
     * `scheme://host[:port]` of a URL, without path or query. This is where a
     * browser looks for the icon of a page that declares none, so it is the
     * fallback candidate — not the page's own directory.
     */
    fun origin(url: String): String? = runCatching {
        val uri = URI(url)
        val host = uri.host ?: return null
        URI(uri.scheme, null, host, uri.port, null, null, null).toString()
    }.getOrNull()

    /**
     * Resolves [reference] — an icon href or a redirect target — against [base],
     * or null when it cannot be parsed.
     *
     * Java's own resolution cannot be used unchanged: a base with no path of its
     * own (`https://host`) makes `URI.resolve` glue the reference onto the
     * authority, so `favicon.php` turned into `https://hostfavicon.php`. Rooting
     * the base first fixes that and leaves a base that does have a path — and with
     * it the parent directory semantics — untouched.
     */
    fun resolve(base: String, reference: String): String? = runCatching {
        if (reference.startsWith(HTTP) || reference.startsWith(HTTPS)) {
            reference
        } else {
            val uri = URI(base)
            val rooted = URI(
                uri.scheme,
                uri.userInfo,
                uri.host,
                uri.port,
                uri.path.orEmpty().ifEmpty { "/" },
                uri.query,
                uri.fragment,
            )
            rooted.resolve(reference).toString()
        }
    }.getOrNull()

    private const val HTTP = "http://"
    private const val HTTPS = "https://"
}

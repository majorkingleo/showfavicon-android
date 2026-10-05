package com.martin.showfavicon

import android.net.Uri

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
    fun host(url: String): String = Uri.parse(url).host.orEmpty()

    /** True when the URL has a host, so `https:///path` is rejected. */
    fun isValid(url: String): Boolean = host(normalize(url)).isNotEmpty()

    /** What the settings list shows: the host without its `www.` prefix. */
    fun displayName(url: String): String = host(url).removePrefix("www.")

    private const val HTTP = "http://"
    private const val HTTPS = "https://"
}

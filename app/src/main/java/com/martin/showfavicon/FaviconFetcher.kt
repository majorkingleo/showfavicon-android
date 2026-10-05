package com.martin.showfavicon

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the favicon of a site.
 *
 * Plain HttpURLConnection on purpose: this is one small GET for the page plus
 * one per icon candidate, once an hour — an HTTP client dependency would be
 * more code than the request itself.
 *
 * Blocking by design; the caller decides about threading.
 */
class FaviconFetcher {

    /** Icon bytes for [siteUrl], or null when nothing usable could be fetched. */
    fun fetch(siteUrl: String): ByteArray? {
        val html = get(siteUrl, MAX_HTML_BYTES, ACCEPT_HTML)?.toString(Charsets.UTF_8)

        val candidates = if (html == null) {
            // The page itself failed, but the icon may still be reachable.
            listOf("${Urls.normalize(siteUrl)}/favicon.ico")
        } else {
            FaviconResolver.candidates(html, siteUrl)
        }

        for (candidate in candidates) {
            val bytes = get(candidate, MAX_ICON_BYTES, ACCEPT_ANY) ?: continue
            // A 404 page served with a 200 status must not end up in the cache.
            if (FaviconStore.isDecodable(bytes)) return bytes
        }
        return null
    }

    private fun get(url: String, maxBytes: Int, accept: String): ByteArray? {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (io: IOException) {
            return null
        }

        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", accept)
            // No transparent gzip: the byte count has to stay predictable.
            connection.setRequestProperty("Accept-Encoding", "identity")

            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.use { it.readAtMost(maxBytes) }
            }
        } catch (io: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_HTML_BYTES = 256 * 1024
        const val MAX_ICON_BYTES = 512 * 1024
        const val ACCEPT_HTML = "text/html,application/xhtml+xml"
        const val ACCEPT_ANY = "image/*,*/*;q=0.8"

        /** A browser string, because CDNs serve their icon only to browsers. */
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}

/** Reads at most [limit] bytes, so a huge response cannot exhaust memory. */
private fun InputStream.readAtMost(limit: Int): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    while (buffer.size() < limit) {
        val read = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
        if (read <= 0) break
        buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
}

package com.martin.showfavicon

import android.util.Log
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
        val page = get(siteUrl, MAX_HTML_BYTES, ACCEPT_HTML)
        Log.d(TAG, "page $siteUrl -> ${page?.size ?: "failed"}")
        val html = page?.toString(Charsets.UTF_8)

        val candidates = if (html == null) {
            // The page itself failed, but the icon may still be reachable.
            listOf("${Urls.normalize(siteUrl)}/favicon.ico")
        } else {
            FaviconResolver.candidates(html, siteUrl)
        }
        Log.d(TAG, "candidates for $siteUrl: $candidates")

        for (candidate in candidates) {
            val bytes = get(candidate, MAX_ICON_BYTES, ACCEPT_ANY) ?: continue
            val kind = when {
                SvgRasterizer.looksLikeSvg(bytes) -> "svg"
                IcoDecoder.looksLikeIco(bytes) -> "ico"
                else -> "raster"
            }
            Log.d(TAG, "candidate $candidate -> ${bytes.size} bytes, $kind")
            // Vector and container icons are converted here, so that the cache holds
            // a PNG either way and nothing downstream knows about the formats.
            val icon = when (kind) {
                "svg" -> SvgRasterizer.toPng(bytes, ICON_SIZE_PX)
                "ico" -> IcoDecoder.toPng(bytes)
                else -> bytes
            }
            Log.d(TAG, "candidate $candidate -> icon ${icon?.size ?: "null"}")
            // A 404 page served with a 200 status must not end up in the cache.
            if (icon != null && FaviconStore.isDecodable(icon)) return icon
        }
        return null
    }

    /**
     * Fetches [url] and follows up to [MAX_REDIRECTS] redirects by hand.
     *
     * `HttpURLConnection` refuses a hop that changes the protocol, and sites do
     * exactly that: an icon requested over https may answer 301 to http, and a page
     * typed as http answers 301 to https.
     */
    private fun get(url: String, maxBytes: Int, accept: String): ByteArray? {
        var target = url
        repeat(MAX_REDIRECTS + 1) {
            val answer = request(target, maxBytes, accept) ?: return null
            if (answer.bytes != null) return answer.bytes

            val location = answer.location ?: return null
            target = Urls.resolve(target, location) ?: return null
        }
        Log.d(TAG, "$target -> too many redirects")
        return null
    }

    /** One request: either with a body, or with the redirect it asks for instead. */
    private fun request(url: String, maxBytes: Int, accept: String): Answer? {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (io: IOException) {
            Log.d(TAG, "$url -> ${io.javaClass.simpleName}: ${io.message}")
            return null
        }

        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // Followed by hand, see get().
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", accept)
            // No transparent gzip: the byte count has to stay predictable.
            connection.setRequestProperty("Accept-Encoding", "identity")

            when (val code = connection.responseCode) {
                in 200..299 -> Answer(connection.inputStream.use { it.readAtMost(maxBytes) }, null)
                in 300..399 -> {
                    val location = connection.getHeaderField("Location")
                    Log.d(TAG, "$url -> HTTP $code to $location")
                    Answer(null, location)
                }

                else -> {
                    Log.d(TAG, "$url -> HTTP $code")
                    Answer(null, null)
                }
            }
        } catch (io: IOException) {
            Log.d(TAG, "$url -> ${io.javaClass.simpleName}: ${io.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Body or redirect target of a single request, never both. */
    private class Answer(val bytes: ByteArray?, val location: String?)

    private companion object {
        const val TAG = "ShowFaviconFetch"
        const val TIMEOUT_MS = 10_000
        const val MAX_HTML_BYTES = 256 * 1024
        const val MAX_ICON_BYTES = 512 * 1024

        /** Hops allowed before a redirect chain is called broken. */
        const val MAX_REDIRECTS = 5

        /** Longest edge of a rasterised vector icon; the widget draws at 96 px. */
        const val ICON_SIZE_PX = 192
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

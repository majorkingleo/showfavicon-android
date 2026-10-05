package com.martin.showfavicon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.IOException

/**
 * Favicon cache in the app's private storage: `filesDir/showfavicon/<host>.png`.
 *
 * The "last fetch failed" flag lives here too, because it answers the same
 * question as the cache: what may the widget show for this host.
 */
class FaviconStore(context: Context) {

    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, DIR_NAME)
    private val failedState = appContext.getSharedPreferences(PREFS_FAILED, Context.MODE_PRIVATE)

    /** Cached icon of a host, or null when nothing usable was fetched yet. */
    fun read(host: String): Bitmap? {
        val file = fileFor(host)
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    /**
     * Stores icon bytes for a host. Returns false when the bytes are not a
     * decodable image, so the caller can count it as a failed fetch.
     */
    fun write(host: String, bytes: ByteArray): Boolean {
        if (host.isEmpty() || !isDecodable(bytes)) return false

        val target = fileFor(host)
        // Write beside the target and move: the widget must never see a partial PNG.
        val temp = File(directory, "${target.name}.tmp")
        return try {
            directory.mkdirs()
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            true
        } catch (io: IOException) {
            temp.delete()
            false
        }
    }

    /** Records that the most recent fetch for this host failed. */
    fun markFailed(host: String) = updateFailed(host, failed = true)

    /** Records that the most recent fetch for this host delivered an icon. */
    fun markOk(host: String) = updateFailed(host, failed = false)

    /** True when the most recent fetch did not deliver an icon. */
    fun hasFailed(host: String): Boolean = failedHosts().contains(host)

    /** Drops cached icons and flags of hosts that are no longer configured. */
    fun prune(keep: Collection<String>) {
        directory.listFiles().orEmpty()
            .filter { file -> file.name.removeSuffix(PNG_SUFFIX).isNotEmpty() }
            .filterNot { file -> file.name.removeSuffix(PNG_SUFFIX) in keep }
            .forEach { it.delete() }

        val current = failedHosts()
        val kept = current.filter { it in keep }.toSet()
        if (kept.size != current.size) {
            failedState.edit { putStringSet(KEY_FAILED, kept) }
        }
    }

    private fun failedHosts(): Set<String> =
        failedState.getStringSet(KEY_FAILED, emptySet()).orEmpty()

    private fun updateFailed(host: String, failed: Boolean) {
        // The set handed out by SharedPreferences must not be modified in place.
        val hosts = failedHosts().toMutableSet()
        if (failed) hosts.add(host) else hosts.remove(host)
        failedState.edit { putStringSet(KEY_FAILED, hosts) }
    }

    private fun fileFor(host: String): File = File(directory, sanitize(host) + PNG_SUFFIX)

    companion object {
        private const val DIR_NAME = "showfavicon"
        private const val PREFS_FAILED = "fetch_state"
        private const val KEY_FAILED = "failed_hosts"
        private const val PNG_SUFFIX = ".png"

        /** Alpha of an icon whose site could not be reached. */
        private const val FAILED_ALPHA = 0.45f

        /** The host becomes a file name, so reduce it to the characters a host may hold. */
        private fun sanitize(host: String): String =
            host.lowercase().replace(NOT_IN_HOST, "_")

        private val NOT_IN_HOST = Regex("[^a-z0-9.-]")

        /** True when BitmapFactory can read the image dimensions from the bytes. */
        fun isDecodable(bytes: ByteArray): Boolean {
            if (bytes.isEmpty()) return false
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            return options.outWidth > 0 && options.outHeight > 0
        }

        /** Scales an icon down so its longer edge is at most [maxSize]. */
        fun scaled(source: Bitmap, maxSize: Int): Bitmap {
            val longest = maxOf(source.width, source.height)
            if (longest <= maxSize) return source

            val factor = maxSize.toFloat() / longest
            val width = maxOf(1, (source.width * factor).toInt())
            val height = maxOf(1, (source.height * factor).toInt())
            val out = createBitmap(width, height)
            val paint = Paint().apply { isFilterBitmap = true }
            Canvas(out).drawBitmap(source, null, Rect(0, 0, width, height), paint)
            return out
        }

        /** Desaturated and dimmed copy: the marker for "the last fetch failed". */
        fun grayed(source: Bitmap): Bitmap {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            // setScale on the alpha row dims the icon without a second canvas pass.
            matrix.postConcat(ColorMatrix().apply { setScale(1f, 1f, 1f, FAILED_ALPHA) })

            val out = createBitmap(source.width, source.height)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(matrix)
                isAntiAlias = true
            }
            Canvas(out).drawBitmap(source, 0f, 0f, paint)
            return out
        }
    }
}

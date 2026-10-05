package com.martin.showfavicon

import android.graphics.Canvas
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.caverock.androidsvg.SVG
import java.io.ByteArrayOutputStream

/**
 * Turns an SVG icon into PNG bytes.
 *
 * `BitmapFactory` cannot decode SVG and Android ships no renderer, so the fetch
 * step rasterises here. The cache therefore stays a plain PNG store and nothing
 * downstream has to know about vectors.
 */
object SvgRasterizer {

    private const val HEAD_BYTES = 512

    /** Fallback edge length for an SVG that declares no size of its own. */
    private const val DEFAULT_SIZE_PX = 96

    /** True when these bytes start like an SVG document. */
    fun looksLikeSvg(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val head = String(bytes, 0, minOf(bytes.size, HEAD_BYTES), Charsets.UTF_8)
        return head.contains("<svg", ignoreCase = true) ||
            head.trimStart().startsWith("<?xml")
    }

    /**
     * Renders [svg] into PNG bytes whose longest edge is [maxSizePx], or null when
     * the document cannot be parsed or drawn.
     */
    fun toPng(svg: ByteArray, maxSizePx: Int): ByteArray? = try {
        val document = SVG.getFromInputStream(svg.inputStream())
        val width = document.documentWidth.takeIf { it > 0f } ?: DEFAULT_SIZE_PX.toFloat()
        val height = document.documentHeight.takeIf { it > 0f } ?: DEFAULT_SIZE_PX.toFloat()

        val scale = maxSizePx / maxOf(width, height)
        val targetWidth = maxOf(1, (width * scale).toInt())
        val targetHeight = maxOf(1, (height * scale).toInt())

        val bitmap = createBitmap(targetWidth, targetHeight)
        // The explicit view port is what scales the document into the bitmap;
        // renderToCanvas() alone would draw it at its own size in the corner.
        document.renderToCanvas(
            Canvas(bitmap),
            RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat()),
        )

        ByteArrayOutputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    } catch (exception: Exception) {
        // A malformed or unsupported document simply is not an icon we can show.
        null
    }
}

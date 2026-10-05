package com.martin.showfavicon

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Turns an ICO icon into PNG bytes.
 *
 * `BitmapFactory` refuses `.ico`, and a site that declares no icon at all still
 * answers `/favicon.ico` — so this is what decides whether such a site gets an
 * icon. The container is a small directory (6 byte header, 16 bytes per entry),
 * and an entry body is either an embedded PNG or a DIB: a `BITMAPINFOHEADER`,
 * the pixel rows, and usually a 1 bit AND mask that carries the transparency.
 *
 * The largest entry wins, because the widget scales an icon down but never up.
 */
object IcoDecoder {

    private const val HEADER_SIZE = 6
    private const val ENTRY_SIZE = 16
    private const val DIB_HEADER_SIZE = 40

    /** Only uncompressed DIBs are read; anything else is skipped. */
    private const val BI_RGB = 0

    /** Accepted bit depths of a DIB body. */
    private val BIT_DEPTHS = intArrayOf(1, 4, 8, 24, 32)

    /** Longest accepted edge, as a guard against a forged header. */
    private const val MAX_EDGE = 512

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /** True when these bytes start like an ICO (or a cursor) directory. */
    fun looksLikeIco(bytes: ByteArray): Boolean {
        if (bytes.size < HEADER_SIZE + ENTRY_SIZE) return false
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // Reserved 0, then type 1 = icon or 2 = cursor, then the entry count.
        return header.getShort(0).toInt() == 0 &&
            header.getShort(2).toInt() in 1..2 &&
            header.getShort(4).toInt() > 0
    }

    /** PNG bytes for the largest image in [bytes], or null when none decodes. */
    fun toPng(bytes: ByteArray): ByteArray? {
        val body = largestEntry(bytes)?.body ?: return null

        // An embedded PNG already is what the cache wants.
        if (body.startsWithPng()) return body

        val bitmap = decodeDib(body) ?: return null
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    /** The entry with the most pixels; a tie goes to the higher bit depth. */
    private fun largestEntry(bytes: ByteArray): Entry? {
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = header.getShort(4).toInt() and 0xFFFF

        var best: Entry? = null
        for (index in 0 until count) {
            val entry = entryAt(bytes, HEADER_SIZE + index * ENTRY_SIZE) ?: continue
            val wins = best?.let {
                entry.pixels > it.pixels || (entry.pixels == it.pixels && entry.bitCount > it.bitCount)
            } ?: true
            if (wins) best = entry
        }
        return best
    }

    private fun entryAt(bytes: ByteArray, at: Int): Entry? {
        if (bytes.size < at + ENTRY_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // A dimension byte of 0 stands for 256, the format's maximum.
        val width = (buffer.get(at).toInt() and 0xFF).takeIf { it != 0 } ?: 256
        val height = (buffer.get(at + 1).toInt() and 0xFF).takeIf { it != 0 } ?: 256
        val bitCount = buffer.getShort(at + 6).toInt() and 0xFFFF
        val size = buffer.getInt(at + 8)
        val offset = buffer.getInt(at + 12)

        if (size <= 0) return null
        if (offset < HEADER_SIZE || offset + size > bytes.size) return null
        return Entry(width, height, bitCount, bytes.copyOfRange(offset, offset + size))
    }

    /** Builds a bitmap from a DIB body: header, pixel rows, optionally a mask. */
    private fun decodeDib(body: ByteArray): Bitmap? {
        if (body.size < DIB_HEADER_SIZE) return null
        val buffer = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)

        val headerSize = buffer.getInt(0)
        // A 12 byte BITMAPCOREHEADER has a different layout and is ancient.
        if (headerSize < DIB_HEADER_SIZE) return null

        val width = buffer.getInt(4)
        val headerHeight = buffer.getInt(8)
        val bitCount = buffer.getShort(14).toInt() and 0xFFFF
        if (buffer.getInt(16) != BI_RGB) return null
        if (bitCount !in BIT_DEPTHS) return null

        val topDown = headerHeight < 0
        // The header height counts the pixel rows *and* the AND mask, except in a
        // top-down DIB, which carries no mask.
        val height = if (topDown) -headerHeight else headerHeight / 2
        if (width <= 0 || height <= 0 || width > MAX_EDGE || height > MAX_EDGE) return null

        val palette = if (bitCount <= 8) readPalette(body, buffer, headerSize, bitCount) else null
        if (bitCount <= 8 && palette == null) return null

        val pixelStart = headerSize + (palette?.size ?: 0) * 4
        val stride = (width * bitCount + 31) / 32 * 4
        if (body.size < pixelStart + stride * height) return null

        val maskStart = pixelStart + stride * height
        val maskStride = (width + 31) / 32 * 4
        val hasMask = !topDown && body.size >= maskStart + maskStride * height

        val pixels = IntArray(width * height)
        for (row in 0 until height) {
            // A DIB stores its rows bottom-up unless it says otherwise.
            val source = if (topDown) row else height - 1 - row
            val rowStart = pixelStart + source * stride
            val target = row * width
            for (column in 0 until width) {
                pixels[target + column] = when (bitCount) {
                    32 -> {
                        val at = rowStart + column * 4
                        val alpha = body[at + 3].toInt() and 0xFF
                        (alpha shl 24) or rgb(body, at)
                    }

                    24 -> (0xFF shl 24) or rgb(body, rowStart + column * 3)
                    else -> palette!![indexAt(body, rowStart, column, bitCount)]
                }
            }
        }

        // 32 bit icons normally carry their alpha in the pixel, but some editors
        // write zero there and leave the transparency to the AND mask. Those pixels
        // have to be made opaque first: otherwise "the mask says visible" still
        // means "alpha zero", and the whole icon renders invisible.
        val pixelsCarryAlpha = bitCount == 32 && pixels.any { (it ushr 24) != 0 }
        if (!pixelsCarryAlpha) {
            if (bitCount == 32) {
                val opaque = 0xFF shl 24
                for (index in pixels.indices) {
                    pixels[index] = pixels[index] or opaque
                }
            }

            if (hasMask) {
                for (row in 0 until height) {
                    val rowStart = maskStart + (height - 1 - row) * maskStride
                    val target = row * width
                    for (column in 0 until width) {
                        val transparent = (body[rowStart + column / 8].toInt() ushr (7 - column % 8)) and 1
                        if (transparent == 1) pixels[target + column] = pixels[target + column] and 0x00FFFFFF
                    }
                }
            }
        }

        return createBitmap(width, height).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /** Palette entries are four bytes each: blue, green, red, unused. */
    private fun readPalette(body: ByteArray, buffer: ByteBuffer, headerSize: Int, bitCount: Int): IntArray? {
        val declared = if (body.size >= 36) buffer.getInt(32) else 0
        val colors = if (declared in 1..256) declared else 1 shl bitCount
        if (body.size < headerSize + colors * 4) return null

        return IntArray(colors) { index ->
            (0xFF shl 24) or rgb(body, headerSize + index * 4)
        }
    }

    /** Palette index of one pixel in a 1, 4 or 8 bit row. */
    private fun indexAt(body: ByteArray, rowStart: Int, column: Int, bitCount: Int): Int {
        val byte = body[rowStart + column * bitCount / 8].toInt() and 0xFF
        return when (bitCount) {
            8 -> byte
            4 -> if (column % 2 == 0) byte ushr 4 else byte and 0x0F
            else -> (byte ushr (7 - column % 8)) and 1
        }
    }

    /** One pixel as opaque ARGB from a blue, green, red triple. */
    private fun rgb(body: ByteArray, at: Int): Int =
        ((body[at + 2].toInt() and 0xFF) shl 16) or
            ((body[at + 1].toInt() and 0xFF) shl 8) or
            (body[at].toInt() and 0xFF)

    private fun ByteArray.startsWithPng(): Boolean =
        size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { this[it] == PNG_SIGNATURE[it] }

    private class Entry(val width: Int, val height: Int, val bitCount: Int, val body: ByteArray) {
        val pixels: Int get() = width * height
    }
}

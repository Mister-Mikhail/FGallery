package com.mistermikhail.fgallery.data

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Stage 6 color-aware SECOND SCREEN for visually similar thumbnails.
 *
 * dHash ignores color and can confuse graded images or consecutive frames.
 * This screen compares RGB channels at corresponding spatial positions.
 * Results are only candidates: small retouches may disappear in a 48px
 * thumbnail. A full-resolution verification and user confirmation are still
 * mandatory before ANY visual-duplicate removal suggestion.
 *
 * Operates exclusively on caller-supplied, already decoded 48x48 posters;
 * never opens or decodes a camera RAW. Call only from an idle worker.
 */
internal object VisualPixelScreen {
    const val GRID = 48
    const val PIXELS = GRID * GRID
    private const val MAX_CHANNEL_DIFFERENCE = 15
    private const val MAX_MEAN_CHANNEL_DIFFERENCE = 5.0

    fun resizedCandidate(a: IntArray, b: IntArray): Boolean {
        require(a.size == PIXELS && b.size == PIXELS)
        if (!hasTexture(a) || !hasTexture(b)) return false
        var error = 0L
        var bad = 0
        for (i in 0 until PIXELS) {
            val d = channelDifference(a[i], b[i])
            error += d.first
            if (d.second > MAX_CHANNEL_DIFFERENCE && ++bad > PIXELS / 100) return false
        }
        return error.toDouble() / (PIXELS * 3) <= MAX_MEAN_CHANNEL_DIFFERENCE
    }

    /**
     * Search conservative crop rectangles. A crop may be rescaled, so its
     * absolute pixel dimensions cannot identify the crop fraction. The
     * aspect ratio constrains rectangle height for each candidate width.
     * Fixed bounds and steps make work predictable on a phone. False negatives
     * are preferable to unsafe duplicate proposals.
     */
    fun cropCandidate(
        original: IntArray, cropped: IntArray,
        originalWidth: Int, originalHeight: Int,
        cropWidth: Int, cropHeight: Int,
        checkActive: () -> Unit = {},
    ): Boolean {
        require(original.size == PIXELS && cropped.size == PIXELS)
        if (minOf(originalWidth, originalHeight, cropWidth, cropHeight) <= 0 ||
            !hasTexture(original) || !hasTexture(cropped)) return false
        val aspectRatio = (originalWidth.toDouble() / originalHeight) /
            (cropWidth.toDouble() / cropHeight)
        for (widthPercent in 40..90 step 5) {
            checkActive()
            val width = widthPercent / 100.0
            val height = width * aspectRatio
            if (height < 0.30 || height > 0.95) continue
            for (iy in 0..8) {
                // Stop promptly during long crop searches, not only between widths.
                checkActive()
                val top = (1.0 - height) * iy / 8.0
                for (ix in 0..8) {
                    val left = (1.0 - width) * ix / 8.0
                    if (cropWindowMatches(original, cropped, left, top, width, height)) return true
                }
            }
        }
        return false
    }

    private fun cropWindowMatches(
        original: IntArray, cropped: IntArray,
        left: Double, top: Double, width: Double, height: Double,
    ): Boolean {
        val count = 24 * 24
        var error = 0.0
        var bad = 0
        for (y in 0 until 24) {
            val v = (y + 0.5) / 24.0
            for (x in 0 until 24) {
                val u = (x + 0.5) / 24.0
                val originalRgb = interpolate(original, left + u * width, top + v * height)
                val croppedRgb = interpolate(cropped, u, v)
                for (channel in 0..2) {
                    val d = abs(originalRgb[channel] - croppedRgb[channel])
                    error += d
                    if (d > MAX_CHANNEL_DIFFERENCE && ++bad > count / 100) return false
                }
            }
        }
        return error / (count * 3) <= MAX_MEAN_CHANNEL_DIFFERENCE
    }

    /** Interpolate in normalized poster coordinates; callers provide 48x48. */
    internal fun interpolate(pixels: IntArray, u: Double, v: Double): IntArray {
        val x = (u * GRID - 0.5).coerceIn(0.0, (GRID - 1).toDouble())
        val y = (v * GRID - 0.5).coerceIn(0.0, (GRID - 1).toDouble())
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = minOf(x0 + 1, GRID - 1)
        val y1 = minOf(y0 + 1, GRID - 1)
        val dx = x - x0
        val dy = y - y0
        val a = pixels[y0 * GRID + x0]
        val b = pixels[y0 * GRID + x1]
        val c = pixels[y1 * GRID + x0]
        val d = pixels[y1 * GRID + x1]
        return IntArray(3) { channel ->
            val shift = 16 - channel * 8
            val aa = (a ushr shift) and 255
            val bb = (b ushr shift) and 255
            val cc = (c ushr shift) and 255
            val dd = (d ushr shift) and 255
            ((aa * (1 - dx) + bb * dx) * (1 - dy) +
                (cc * (1 - dx) + dd * dx) * dy).roundToInt()
        }
    }

    private fun channelDifference(a: Int, b: Int): Pair<Int, Int> {
        var sum = 0
        var max = 0
        for (shift in intArrayOf(16, 8, 0)) {
            val d = abs(((a ushr shift) and 255) - ((b ushr shift) and 255))
            sum += d
            max = maxOf(max, d)
        }
        return sum to max
    }

    /** Reject flat posters where many unrelated pictures hash identically. */
    private fun hasTexture(pixels: IntArray): Boolean {
        var total = 0L
        for (y in 0 until GRID) for (x in 1 until GRID) {
            total += channelDifference(pixels[y * GRID + x], pixels[y * GRID + x - 1]).first
        }
        return total > GRID.toLong() * (GRID - 1) * 3
    }
}

package com.mistermikhail.fgallery.data

/**
 * Strict decoded-pixel verification for Stage 6 visual candidates.
 *
 * The caller must decode both complete source images, in the same orientation
 * and sRGB color space, without thumbnail substitution. No hash is accepted as
 * proof. This deliberately conservative verifier only accepts exact aligned
 * crops or exact nearest-neighbour integer downscales. Bilinear/JPEG resizes
 * remain unverified rather than risking false positive deletion.
 *
 * Results are informational only: NEVER authorize trash from this API.
 */
internal object VisualDuplicatePixelVerification {
    internal data class Pixels(val width: Int, val height: Int, val argb: IntArray) {
        fun valid(): Boolean = width > 0 && height > 0 &&
            width.toLong() * height.toLong() == argb.size.toLong()
    }

    internal enum class Result { EXACT_PIXELS, EXACT_CROP, EXACT_INTEGER_RESIZE, UNVERIFIED }

    fun verify(first: Pixels, second: Pixels): Result {
        if (!first.valid() || !second.valid()) return Result.UNVERIFIED
        if (first.width == second.width && first.height == second.height) {
            return if (first.argb.contentEquals(second.argb)) Result.EXACT_PIXELS else Result.UNVERIFIED
        }
        val large = if (first.width.toLong() * first.height >= second.width.toLong() * second.height) first else second
        val small = if (large === first) second else first
        // No geometry-only matches. Check every pixel, including RGB channels,
        // so dHash collisions, color grading and local retouching are rejected.
        if (large.width % small.width == 0 && large.height % small.height == 0) {
            val scaleX = large.width / small.width
            val scaleY = large.height / small.height
            if (scaleX == scaleY && scaleX > 1 && matchesIntegerScale(large, small, scaleX)) {
                return Result.EXACT_INTEGER_RESIZE
            }
        }
        if (large.width >= small.width && large.height >= small.height &&
            matchesExactCrop(large, small)) return Result.EXACT_CROP
        return Result.UNVERIFIED
    }

    private fun matchesIntegerScale(large: Pixels, small: Pixels, scale: Int): Boolean {
        // A true nearest-neighbour enlargement repeats each source pixel over
        // the WHOLE scale x scale block. Sampling just its top-left corner
        // falsely accepts edited or unrelated images with matching samples.
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val expected = small.argb[y * small.width + x]
            for (dy in 0 until scale) for (dx in 0 until scale) {
                if (large.argb[(y * scale + dy) * large.width + x * scale + dx] != expected) {
                    return false
                }
            }
        }
        return true
    }

    private fun matchesExactCrop(large: Pixels, small: Pixels): Boolean {
        // A large offset is normal for real camera crops. Scan candidate
        // positions using a small set of anchors, then compare every pixel.
        // Bound expensive full comparisons on uniform/repetitive images.
        val maxTop = large.height - small.height
        val maxLeft = large.width - small.width
        val anchorX = small.width / 2
        val anchorY = small.height / 2
        val first = small.argb[0]
        val center = small.argb[anchorY * small.width + anchorX]
        val last = small.argb.last()
        var fullComparisons = 0
        for (top in 0..maxTop) {
            for (left in 0..maxLeft) {
                val base = top * large.width + left
                if (large.argb[base] != first ||
                    large.argb[base + anchorY * large.width + anchorX] != center ||
                    large.argb[base + (small.height - 1) * large.width + small.width - 1] != last
                ) continue
                if (++fullComparisons > 128) return false
                var equal = true
                loop@ for (y in 0 until small.height) for (x in 0 until small.width) {
                    if (small.argb[y * small.width + x] !=
                        large.argb[base + y * large.width + x]) {
                        equal = false
                        break@loop
                    }
                }
                if (equal) return true
            }
        }
        return false
    }
}

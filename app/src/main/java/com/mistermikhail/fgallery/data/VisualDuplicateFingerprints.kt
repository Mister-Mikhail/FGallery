package com.mistermikhail.fgallery.data

/**
 * A 64-bit difference hash of an already decoded 9x8 RGB thumbnail.
 * Hashes are only a cheap shortlist for potential resizes; they do not prove
 * image equality (a color edit can have an identical luminance dHash).
 * No original image, especially a camera RAW, is decoded by this helper.
 */
internal object VisualDuplicateFingerprints {
    const val SAMPLE_WIDTH = 9
    const val SAMPLE_HEIGHT = 8
    const val SAMPLE_PIXELS = SAMPLE_WIDTH * SAMPLE_HEIGHT

    fun differenceHash(argb: IntArray): String {
        require(argb.size == SAMPLE_PIXELS) { "Expected a 9x8 RGB sample" }
        var bits = 0L
        for (y in 0 until SAMPLE_HEIGHT) {
            for (x in 0 until SAMPLE_WIDTH - 1) {
                val left = luminance(argb[y * SAMPLE_WIDTH + x])
                val right = luminance(argb[y * SAMPLE_WIDTH + x + 1])
                bits = (bits shl 1) or (if (left > right) 1L else 0L)
            }
        }
        return java.lang.Long.toUnsignedString(bits, 16).padStart(16, '0')
    }

    fun hammingDistance(first: String, second: String): Int? {
        if (first.length != 16 || second.length != 16 ||
            !first.all(::isHexDigit) || !second.all(::isHexDigit)) return null
        return java.lang.Long.bitCount(
            java.lang.Long.parseUnsignedLong(first, 16) xor
                java.lang.Long.parseUnsignedLong(second, 16)
        )
    }

    private fun isHexDigit(c: Char): Boolean = c in '0'..'9' ||
        c in 'a'..'f' || c in 'A'..'F'

    private fun luminance(argb: Int): Int {
        val red = (argb ushr 16) and 0xff
        val green = (argb ushr 8) and 0xff
        val blue = argb and 0xff
        return red * 299 + green * 587 + blue * 114
    }
}

/**
 * This predicate is deliberately named *candidate*. Do not expose its result
 * as a duplicate, and never delete based on it. Stage 6 still requires a
 * pixel-level verifier that rejects color grading, retouching and burst shots.
 * Crops need their own alignment search and are NOT matched by this method.
 */
internal fun potentialResizedDuplicate(a: CatalogMediaRow, b: CatalogMediaRow): Boolean {
    if (a.uri == b.uri || a.recycled || b.recycled ||
        a.kind != "IMAGE" || b.kind != "IMAGE" ||
        a.width < 64 || b.width < 64 || a.height < 64 || b.height < 64 ||
        a.name.endsWith(".gif", true) || b.name.endsWith(".gif", true) ||
        isCameraRawImage(a.name) || isCameraRawImage(b.name)) return false
    if (a.dateTakenMillis > 0L && b.dateTakenMillis > 0L &&
        kotlin.math.abs(a.dateTakenMillis - b.dateTakenMillis) > 2_000L) return false
    if (!a.exifCamera.isNullOrBlank() && !b.exifCamera.isNullOrBlank() &&
        !a.exifCamera.equals(b.exifCamera, ignoreCase = true)) return false
    // Use integer products, not rounded float aspect ratios.
    val lhs = a.width.toLong() * b.height
    val rhs = b.width.toLong() * a.height
    if (kotlin.math.abs(lhs - rhs) > maxOf(lhs, rhs) * 0.015) return false
    val first = a.perceptualHash ?: return false
    val second = b.perceptualHash ?: return false
    return (VisualDuplicateFingerprints.hammingDistance(first, second) ?: return false) <= 5
}

/** Camera RAW + JPEG from one shutter press is a pair, not a visual duplicate. */
internal fun isCameraRawImage(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in setOf(
        "dng", "cr2", "cr3", "nef", "nrw", "arw", "sr2", "srf",
        "orf", "rw2", "raf", "pef", "raw", "3fr", "fff", "iiq",
    )

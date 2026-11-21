package com.mistermikhail.fgallery.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read actual source pixels, not cached thumbnails. A bounded decode prevents
 * the review UI from allocating enormous camera panoramas/RAW bitmaps.
 * Failure is UNVERIFIED, never evidence of duplication or permission to trash.
 */
internal object VisualDuplicateSourceVerification {
    private const val MAX_PIXELS = 12_000_000L
    // Both decoded bitmaps and both ARGB arrays coexist during comparison.
    // Bound their combined size, not just each image independently.
    private const val MAX_PAIR_PIXELS = 12_000_000L

    suspend fun verify(context: Context, first: CatalogMediaRow, second: CatalogMediaRow):
        VisualDuplicatePixelVerification.Result = withContext(Dispatchers.IO) {
        if (!potentialVisualDuplicate(first, second)) {
            return@withContext VisualDuplicatePixelVerification.Result.UNVERIFIED
        }
        // Reject oversized pairs before allocating either full-resolution bitmap.
        // Catalog dimensions are hints only; decode() still checks real dimensions.
        if (!safeVisualPairDimensions(first.width, first.height, second.width, second.height,
                MAX_PAIR_PIXELS)) return@withContext VisualDuplicatePixelVerification.Result.UNVERIFIED
        val a = decode(context, first.uri) ?: return@withContext VisualDuplicatePixelVerification.Result.UNVERIFIED
        val remaining = MAX_PAIR_PIXELS - a.width.toLong() * a.height
        val b = decode(context, second.uri, remaining) ?: return@withContext VisualDuplicatePixelVerification.Result.UNVERIFIED
        VisualDuplicatePixelVerification.verify(a, b)
    }

    private fun decode(context: Context, uriString: String, maxPixels: Long = MAX_PIXELS): VisualDuplicatePixelVerification.Pixels? =
        runCatching {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return null
            if (!safeSourceDimensions(bounds.outWidth, bounds.outHeight, minOf(MAX_PIXELS, maxPixels))) return null
            val orientation = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: return null
            if (orientation !in 1..8) return null
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null
            try {
                if (!safeSourceDimensions(bitmap.width, bitmap.height, minOf(MAX_PIXELS, maxPixels)) ||
                    bitmap.width != bounds.outWidth || bitmap.height != bounds.outHeight) return null
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                orientSourcePixels(VisualDuplicatePixelVerification.Pixels(bitmap.width, bitmap.height, pixels), orientation)
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
}

internal fun safeSourceDimensions(width: Int, height: Int, maxPixels: Long = 12_000_000L): Boolean =
    width > 0 && height > 0 && width.toLong() * height.toLong() <= maxPixels

/** A strict memory budget for the two decoded source images together. */
internal fun safeVisualPairDimensions(
    firstWidth: Int, firstHeight: Int, secondWidth: Int, secondHeight: Int,
    maxPixels: Long = 12_000_000L,
): Boolean = safeSourceDimensions(firstWidth, firstHeight, maxPixels) &&
    safeSourceDimensions(secondWidth, secondHeight, maxPixels) &&
    firstWidth.toLong() * firstHeight + secondWidth.toLong() * secondHeight <= maxPixels

/** Apply all eight EXIF orientations to full source pixels, never cached thumbnails. */
internal fun orientSourcePixels(
    source: VisualDuplicatePixelVerification.Pixels,
    orientation: Int,
): VisualDuplicatePixelVerification.Pixels? {
    if (!source.valid() || orientation !in 1..8) return null
    if (orientation == 1) return source
    val w = source.width
    val h = source.height
    val swap = orientation in 5..8
    val outW = if (swap) h else w
    val outH = if (swap) w else h
    val result = IntArray(source.argb.size)
    for (y in 0 until outH) for (x in 0 until outW) {
        val sx: Int
        val sy: Int
        when (orientation) {
            2 -> { sx = w - 1 - x; sy = y }
            3 -> { sx = w - 1 - x; sy = h - 1 - y }
            4 -> { sx = x; sy = h - 1 - y }
            5 -> { sx = y; sy = x }
            6 -> { sx = y; sy = h - 1 - x }
            7 -> { sx = w - 1 - y; sy = h - 1 - x }
            8 -> { sx = w - 1 - y; sy = x }
            else -> { sx = x; sy = y }
        }
        result[y * outW + x] = source.argb[sy * w + sx]
    }
    return VisualDuplicatePixelVerification.Pixels(outW, outH, result)
}

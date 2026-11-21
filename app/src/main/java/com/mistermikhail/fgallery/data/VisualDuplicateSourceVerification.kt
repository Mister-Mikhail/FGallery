package com.mistermikhail.fgallery.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
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
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null
            try {
                if (!safeSourceDimensions(bitmap.width, bitmap.height, minOf(MAX_PIXELS, maxPixels)) ||
                    bitmap.width != bounds.outWidth || bitmap.height != bounds.outHeight) return null
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                VisualDuplicatePixelVerification.Pixels(bitmap.width, bitmap.height, pixels)
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

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

    suspend fun verify(context: Context, first: CatalogMediaRow, second: CatalogMediaRow):
        VisualDuplicatePixelVerification.Result = withContext(Dispatchers.IO) {
        if (!potentialResizedDuplicate(first, second)) {
            return@withContext VisualDuplicatePixelVerification.Result.UNVERIFIED
        }
        val a = decode(context, first.uri) ?: return@withContext VisualDuplicatePixelVerification.Result.UNVERIFIED
        val b = decode(context, second.uri) ?: return@withContext VisualDuplicatePixelVerification.Result.UNVERIFIED
        VisualDuplicatePixelVerification.verify(a, b)
    }

    private fun decode(context: Context, uriString: String): VisualDuplicatePixelVerification.Pixels? =
        runCatching {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return null
            if (!safeSourceDimensions(bounds.outWidth, bounds.outHeight, MAX_PIXELS)) return null
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null
            try {
                if (!safeSourceDimensions(bitmap.width, bitmap.height, MAX_PIXELS) ||
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

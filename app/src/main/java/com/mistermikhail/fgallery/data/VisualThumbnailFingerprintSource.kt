package com.mistermikhail.fgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Hash ONLY an existing cached poster. Never start a decode, open a RAW,
 * generate thumbnails, or hold the bitmap after a single 9x8 sample.
 * A missing/evicted cache entry is retried on a future idle pass.
 */
internal object VisualThumbnailFingerprintSource {
    fun cachedDifferenceHash(
        context: Context,
        row: CatalogMediaRow,
        checkActive: () -> Unit,
    ): String? {
        if (row.kind != "IMAGE" || row.width < 64 || row.height < 64 ||
            row.name.endsWith(".gif", ignoreCase = true)) return null
        val item = MediaItem(
            row.mediaId, Uri.parse(row.uri), row.name, row.mimeType,
            MediaKind.IMAGE, row.dateTakenMillis, row.width, row.height,
            row.durationMillis, row.album, row.relativePath, row.sizeBytes,
            row.dateModifiedMillis, row.storageId, row.storageName,
            row.folderTarget, row.sourcePath,
        )
        val poster = ThumbnailCache.fileFor(context, item).takeIf { it.isFile && it.length() > 0L }
            ?: ThumbnailCache.fileFor(context, item, highQuality = true)
                .takeIf { it.isFile && it.length() > 0L }
            ?: return null
        checkActive()
        // Decode a compact 400px poster, never the original. Downsampling an
        // HQ-only poster avoids allocating a full 1920px bitmap.
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(poster.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        options.inJustDecodeBounds = false
        options.inSampleSize = if (maxOf(options.outWidth, options.outHeight) > 800) 8 else 1
        val bitmap = BitmapFactory.decodeFile(poster.path, options) ?: return null
        try {
            checkActive()
            val sample = Bitmap.createScaledBitmap(bitmap,
                VisualDuplicateFingerprints.SAMPLE_WIDTH,
                VisualDuplicateFingerprints.SAMPLE_HEIGHT, true)
            try {
                val pixels = IntArray(VisualDuplicateFingerprints.SAMPLE_PIXELS)
                sample.getPixels(pixels, 0, VisualDuplicateFingerprints.SAMPLE_WIDTH,
                    0, 0, VisualDuplicateFingerprints.SAMPLE_WIDTH,
                    VisualDuplicateFingerprints.SAMPLE_HEIGHT)
                checkActive()
                return VisualDuplicateFingerprints.differenceHash(pixels)
            } finally {
                if (sample !== bitmap) sample.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }
}

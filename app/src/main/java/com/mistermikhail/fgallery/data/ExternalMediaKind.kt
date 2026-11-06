package com.mistermikhail.fgallery.data

import java.util.Locale

/**
 * Classifies a document opened by another app. SAF providers frequently report
 * application/octet-stream for AVI, M4V and camera RAW files.
 * Prefer the actual display name (OpenableColumns) over the provider MIME.
 */
internal object ExternalMediaKind {
    private val videos = setOf(
        "avi", "m4v", "mp4", "mov", "mkv", "webm", "3gp", "3g2",
        "wmv", "mpeg", "mpg", "ts", "m2ts", "mts"
    )
    private val raws = setOf(
        "dng", "cr2", "cr3", "nef", "nrw", "arw", "raf", "rw2",
        "orf", "pef", "srw", "iiq", "3fr", "fff", "raw", "erf",
        "k25", "kdc", "mos", "mrw", "obm", "qtk", "rdc", "sti"
    )
    private val images = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "avif",
        "heic", "heif", "tif", "tiff", "jxl", "ico"
    )

    fun classify(name: String, reportedMime: String?): MediaKind? {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            extension in videos -> MediaKind.VIDEO
            extension in raws -> MediaKind.RAW
            extension == "pdf" -> MediaKind.PDF
            extension == "svg" -> MediaKind.SVG
            extension in images -> MediaKind.IMAGE
            reportedMime?.startsWith("video/", ignoreCase = true) == true -> MediaKind.VIDEO
            reportedMime?.startsWith("image/", ignoreCase = true) == true -> MediaKind.IMAGE
            reportedMime == "application/pdf" -> MediaKind.PDF
            reportedMime == "image/svg+xml" -> MediaKind.SVG
            else -> null
        }
    }
}

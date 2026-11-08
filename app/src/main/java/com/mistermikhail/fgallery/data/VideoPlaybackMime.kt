package com.mistermikhail.fgallery.data

import java.util.Locale

/**
 * Container hint for Media3, not a promise that the device can decode its codec.
 * SAF and third-party providers frequently return null, an image MIME type or
 * application/octet-stream even for supported video filenames.
 */
internal object VideoPlaybackMime {
    fun forFile(name: String, reportedMime: String?): String? {
        val extension = name.trim().substringAfterLast('.', "").lowercase(Locale.ROOT)
        // Prefer a recognized extension over an unreliable provider MIME.
        return when (extension) {
            "m4v", "mp4", "m4p", "mov" -> "video/mp4"
            "avi" -> "video/avi"
            "wmv" -> "video/x-ms-wmv"
            "mpeg", "mpg" -> "video/mpeg"
            "mkv", "mk3d" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp", "3gpp", "3g2" -> "video/3gpp"
            "ts", "m2ts", "mts" -> "video/mp2t"
            else -> when (val mime = reportedMime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)) {
                "application/x-m4v", "video/x-m4v" -> "video/mp4"
                "application/x-avi", "application/avi", "video/x-msvideo", "video/avi", "video/msvideo" -> "video/avi"
                "video/quicktime" -> "video/mp4"
                else -> mime?.takeIf { it.startsWith("video/") }
            }
        }
    }
}

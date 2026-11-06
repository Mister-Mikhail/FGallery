package com.mistermikhail.fgallery.data

import java.util.Locale

/**
 * Container hint for Media3, not a promise that the device can decode its codec.
 * SAF and third-party providers frequently return null, image/* or
 * application/octet-stream even for supported video filenames.
 */
internal object VideoPlaybackMime {
    fun forFile(name: String, reportedMime: String?): String? {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        // Prefer a recognized extension over an unreliable provider MIME.
        return when (extension) {
            "m4v", "mp4", "m4p", "m4a", "mov" -> "video/mp4"
            "avi" -> "video/avi"
            "mkv", "mk3d" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp", "3gpp", "3g2" -> "video/3gpp"
            "ts", "m2ts", "mts" -> "video/mp2t"
            else -> reportedMime?.takeIf { it.startsWith("video/", ignoreCase = true) }
        }
    }
}

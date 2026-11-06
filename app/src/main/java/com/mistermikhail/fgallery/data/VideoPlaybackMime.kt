package com.mistermikhail.fgallery.data

/**
 * A container hint for Media3. MediaStore/SAF may return application/octet-stream
 * or null for a recognized video filename; this is not the codec MIME type.
 */
internal object VideoPlaybackMime {
    fun forFile(name: String, reportedMime: String?): String? {
        return when (name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)) {
            "m4v", "mp4" -> "video/mp4"
            "avi" -> "video/avi"
            else -> reportedMime?.takeIf { it.startsWith("video/", ignoreCase = true) }
        }
    }
}

package com.mistermikhail.fgallery.data

import android.net.Uri

enum class MediaKind { IMAGE, VIDEO, RAW }

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val kind: MediaKind,
    val dateTakenMillis: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long,
    val album: String,
    val relativePath: String,
    val sizeBytes: Long,
    val dateModifiedMillis: Long = 0L,
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f
}

package com.mistermikhail.fgallery.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MediaRepository(private val context: Context) {
    suspend fun loadMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
        )
        val selection =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val sort = "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns.DATE_ADDED} DESC"

        buildList {
            resolver.query(collection, projection, selection, args, sort)?.use { cursor ->
                val idC = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeC = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val nameC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val takenC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val addedC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val widthC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightC = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val durationC = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
                val albumC = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idC)
                    val mediaType = cursor.getInt(typeC)
                    val name = cursor.getString(nameC).orEmpty()
                    val mime = cursor.getString(mimeC)
                    val kind = when {
                        mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaKind.VIDEO
                        isRaw(name, mime) -> MediaKind.RAW
                        else -> MediaKind.IMAGE
                    }
                    val taken = cursor.getLong(takenC)
                    val added = cursor.getLong(addedC) * 1000L
                    add(
                        MediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            name = name,
                            mimeType = mime,
                            kind = kind,
                            dateTakenMillis = taken.takeIf { it > 0L } ?: added,
                            width = cursor.getInt(widthC),
                            height = cursor.getInt(heightC),
                            durationMillis = cursor.getLong(durationC),
                            album = cursor.getString(albumC).orEmpty().ifBlank { "Без альбома" },
                        )
                    )
                }
            }
        }
    }

    private fun isRaw(name: String, mimeType: String?): Boolean {
        val n = name.lowercase(Locale.ROOT)
        val m = mimeType?.lowercase(Locale.ROOT).orEmpty()
        return RAW_EXTENSIONS.any(n::endsWith) || RAW_MIME_HINTS.any(m::contains)
    }

    companion object {
        private val RAW_EXTENSIONS = listOf(
            ".dng", ".cr2", ".cr3", ".nef", ".nrw", ".arw", ".sr2", ".srf",
            ".orf", ".rw2", ".raf", ".pef", ".raw", ".3fr", ".fff", ".iiq",
        )
        private val RAW_MIME_HINTS = listOf(
            "dng", "raw", "canon", "nikon", "sony", "olympus", "panasonic", "fujifilm", "pentax",
        )
    }
}

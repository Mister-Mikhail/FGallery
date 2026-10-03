package com.mistermikhail.fgallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MediaRepository(private val context: Context) {
    suspend fun loadMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        queryMedia(trashedOnly = false)
    }

    suspend fun loadTrash(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            emptyList()
        } else {
            queryMedia(trashedOnly = true)
        }
    }

    private fun queryMedia(trashedOnly: Boolean): List<MediaItem> {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
        )
        val selection =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val sort =
            "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns.DATE_ADDED} DESC"

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
                putInt(
                    MediaStore.QUERY_ARG_MATCH_TRASHED,
                    if (trashedOnly) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE,
                )
            }
            resolver.query(collection, projection, queryArgs, null)
        } else {
            if (trashedOnly) return emptyList()
            resolver.query(collection, projection, selection, args, sort)
        }

        return buildList {
            cursor?.use {
                val idC = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeC = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val nameC = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeC = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val takenC = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val addedC = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val modifiedC = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val widthC = it.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightC = it.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val sizeC = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val pathC = it.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val durationC = it.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
                val albumC = it.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)

                while (it.moveToNext()) {
                    val id = it.getLong(idC)
                    val mediaType = it.getInt(typeC)
                    val name = it.getString(nameC).orEmpty()
                    val mime = it.getString(mimeC)
                    val kind = when {
                        mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaKind.VIDEO
                        isRaw(name, mime) -> MediaKind.RAW
                        else -> MediaKind.IMAGE
                    }
                    val taken = it.getLong(takenC)
                    val added = it.getLong(addedC) * 1000L

                    val itemCollection =
                        if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        } else {
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        }

                    add(
                        MediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(itemCollection, id),
                            name = name,
                            mimeType = mime,
                            kind = kind,
                            dateTakenMillis = taken.takeIf { value -> value > 0L } ?: added,
                            width = it.getInt(widthC),
                            height = it.getInt(heightC),
                            durationMillis = it.getLong(durationC),
                            album = it.getString(albumC).orEmpty().ifBlank { "Без альбома" },
                            relativePath = it.getString(pathC).orEmpty(),
                            sizeBytes = it.getLong(sizeC),
                            dateModifiedMillis = it.getLong(modifiedC) * 1000L,
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

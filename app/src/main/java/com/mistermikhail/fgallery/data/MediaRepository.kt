package com.mistermikhail.fgallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
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

    /**
     * Incremental MediaStore scan (Android 11+). A generation snapshot is
     * captured BEFORE the previous scan, so changes that arrive mid-scan are
     * included again and never silently skipped. API29/limited access falls
     * back to the complete indexed query.
     */
    suspend fun loadIncremental(
        previous: List<MediaItem>,
        savedVolumes: Map<String, CatalogVolumeRow>,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !StorageFolders.hasFileAccess() ||
            savedVolumes.isEmpty()) return@withContext queryMedia(trashedOnly = false)
        MediaStore.getExternalVolumeNames(context).flatMap { volume ->
            val old = savedVolumes[volume]
            val currentVersion = runCatching { MediaStore.getVersion(context, volume) }.getOrNull()
            val currentGeneration = runCatching { MediaStore.getGeneration(context, volume) }.getOrNull()
            val indexed = previous.filter {
                it.storageId == volume && it.uri.authority == MediaStore.AUTHORITY
            }
            when {
                old == null || old.mediaStoreGeneration <= 0L ||
                    currentVersion == null || currentGeneration == null ||
                    old.mediaStoreVersion != currentVersion ||
                    currentGeneration < old.mediaStoreGeneration -> queryVolume(volume, false)
                currentGeneration == old.mediaStoreGeneration -> indexed
                else -> {
                    val changed = queryVolume(volume, false, old.mediaStoreGeneration)
                    val ids = runCatching { currentIds(volume) }.getOrNull()
                        ?: return@flatMap queryVolume(volume, false)
                    (indexed.filter { item -> runCatching { ContentUris.parseId(item.uri) }.getOrNull() in ids } +
                        changed).distinctBy { it.uriKey }.let { result ->
                            // Changed records replace outdated metadata for the same URI.
                            val delta = changed.associateBy { it.uriKey }
                            result.map { delta[it.uriKey] ?: it }
                        }
                }
            }
        }
    }

    private fun currentIds(volume: String): Set<Long> {
        val ids = HashSet<Long>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri(volume),
            arrayOf(MediaStore.Files.FileColumns._ID), null, null, null,
        )?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            while (cursor.moveToNext()) ids.add(cursor.getLong(col))
        } ?: error("Не удалось проверить удалённые MediaStore ID")
        return ids
    }


    suspend fun loadTrash(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            emptyList()
        } else {
            queryMedia(trashedOnly = true)
        }
    }

    private fun queryMedia(trashedOnly: Boolean): List<MediaItem> = MediaStore.getExternalVolumeNames(context).flatMap { volume ->
        runCatching { queryVolume(volume, trashedOnly) }.getOrDefault(emptyList())
    }

    private fun queryVolume(volume: String, trashedOnly: Boolean, since: Long? = null): List<MediaItem> {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(volume)
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
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
        val mediaSelection =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?" +
                if (StorageFolders.hasFileAccess()) (RAW_EXTENSIONS + listOf(".tif", ".tiff")).joinToString("", prefix = "") { " OR LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ?" } else ""
        val selection = "($mediaSelection)" +
            if (since != null) " AND ${MediaStore.MediaColumns.GENERATION_MODIFIED} > ?" else ""
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        ) + (if (StorageFolders.hasFileAccess()) (RAW_EXTENSIONS + listOf(".tif", ".tiff")).map { "%$it" }.toTypedArray() else emptyArray()) +
            (if (since != null) arrayOf(since.toString()) else emptyArray())
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
                val dataC = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val root = StorageAccess.mounted(context).firstOrNull { r -> r.id == volume }
                    ?: StorageRoot(volume, volume, "")
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
                    val sourcePath = it.getString(dataC).orEmpty()
                    if (sourcePath.contains("/${StorageAccess.BIN}/") || (StorageFolders.hasFileAccess() && sourcePath.isNotBlank() && !java.io.File(sourcePath).exists())) continue
                    val id = it.getLong(idC)
                    val mediaType = it.getInt(typeC)
                    val name = it.getString(nameC).orEmpty()
                    val mime = it.getString(mimeC)
                    val kind = when {
                        mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaKind.VIDEO
                        mime == "image/svg+xml" || name.endsWith(".svg", true) -> MediaKind.SVG
                        isRaw(name, mime) -> MediaKind.RAW
                        else -> MediaKind.IMAGE
                    }
                    val taken = it.getLong(takenC)
                    val added = it.getLong(addedC) * 1000L

                    val itemCollection =
                        if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                            MediaStore.Video.Media.getContentUri(volume)
                        } else if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                            MediaStore.Images.Media.getContentUri(volume)
                        } else {
                            collection
                        }

                    add(
                        MediaItem(
                            id = StorageAccess.stableId(ContentUris.withAppendedId(itemCollection, id).toString()),
                            uri = ContentUris.withAppendedId(itemCollection, id),
                            name = name,
                            mimeType = mime,
                            kind = kind,
                            dateTakenMillis = taken.takeIf { value -> value > 0L } ?: added,
                            width = it.getInt(widthC),
                            height = it.getInt(heightC),
                            durationMillis = it.getLong(durationC),
                            album = it.getString(albumC).orEmpty().ifBlank { "Без альбома" }.let { name -> if (volume == "external_primary") name else "${root.name} · $name" },
                            relativePath = it.getString(pathC).orEmpty(),
                            sizeBytes = it.getLong(sizeC),
                            dateModifiedMillis = if (StorageFolders.hasFileAccess() && sourcePath.isNotBlank()) java.io.File(sourcePath).lastModified() else it.getLong(modifiedC) * 1000L,
                            storageId = volume, storageName = root.name, sourcePath = it.getString(dataC).orEmpty(),
                            folderTarget = it.getString(dataC)?.let { path -> java.io.File(path).parent }.orEmpty(),
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


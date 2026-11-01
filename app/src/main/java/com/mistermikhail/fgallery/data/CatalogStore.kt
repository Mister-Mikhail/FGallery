package com.mistermikhail.fgallery.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Non-destructive Stage 5 catalogue migration.
 *
 * The old JSON snapshot remains an independent rollback source until the
 * complete incremental scanner is accepted. Only database rows are changed;
 * files, storage permissions and recycle-bin originals are never modified.
 */
internal object CatalogStore {
    private const val PREFS = "fgallery_catalog_status"
    private const val PERMISSION_KEY = "permission_at_last_scan"

    suspend fun restore(context: Context, hasPermission: Boolean): LibrarySnapshot.Value? =
        withContext(Dispatchers.IO) {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!preferences.contains(PERMISSION_KEY) ||
                preferences.getBoolean(PERMISSION_KEY, false) != hasPermission) return@withContext null
            val rows = CatalogDatabase.get(context).catalog().allMedia()
            if (rows.isEmpty()) return@withContext null
            val items = rows.mapNotNull { row ->
                runCatching {
                    MediaItem(
                        row.mediaId, Uri.parse(row.uri), row.name, row.mimeType,
                        MediaKind.valueOf(row.kind), row.dateTakenMillis,
                        row.width, row.height, row.durationMillis, row.album,
                        row.relativePath, row.sizeBytes, row.dateModifiedMillis,
                        row.storageId, row.storageName, row.folderTarget, row.sourcePath,
                    )
                }.getOrNull()
            }
            LibrarySnapshot.Value(
                items,
                rows.filter { it.recycled }.mapTo(hashSetOf()) { it.uri },
                hasPermission,
            )
        }

    suspend fun persist(context: Context, snapshot: LibrarySnapshot.Value) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val media = snapshot.items.map { item ->
            CatalogMediaRow(
                uri = item.uriKey,
                mediaId = item.id,
                name = item.name,
                mimeType = item.mimeType,
                kind = item.kind.name,
                dateTakenMillis = item.dateTakenMillis,
                width = item.width,
                height = item.height,
                durationMillis = item.durationMillis,
                album = item.album,
                relativePath = item.relativePath,
                sizeBytes = item.sizeBytes,
                dateModifiedMillis = item.dateModifiedMillis,
                storageId = item.storageId,
                storageName = item.storageName,
                folderTarget = item.folderTarget,
                sourcePath = item.sourcePath,
                recycled = item.uriKey in snapshot.bin,
            )
        }
        val folders = snapshot.items
            .groupBy { it.storageId to it.folderTarget.ifBlank { it.relativePath } }
            .map { (folder, _) ->
                val path = folder.second
                val mtime = if (path.startsWith("/")) File(path).lastModified() else 0L
                CatalogFolderRow(
                    key = "${folder.first}/$path",
                    storageId = folder.first,
                    relativePath = path,
                    lastModifiedMillis = mtime,
                    lastScannedMillis = now,
                )
            }
        val volumes = snapshot.items.groupBy { it.storageId }.map { (id, items) ->
            val generation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                runCatching { MediaStore.getGeneration(context, id) }.getOrDefault(0L) else 0L
            val version = runCatching { MediaStore.getVersion(context, id) }.getOrDefault("")
            CatalogVolumeRow(
                storageId = id,
                displayName = items.first().storageName,
                mediaStoreGeneration = generation,
                mediaStoreVersion = version,
                lastScannedMillis = now,
                connected = true,
            )
        }
        // Room transaction either commits the entire snapshot or leaves the
        // prior catalogue unchanged. Never use destructive fallback migrations.
        CatalogDatabase.get(context).catalog().replaceSnapshot(media, folders, volumes)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(PERMISSION_KEY, snapshot.permission).apply()
    }

    suspend fun removeUris(context: Context, uris: Set<String>) = withContext(Dispatchers.IO) {
        if (uris.isNotEmpty()) CatalogDatabase.get(context).catalog().deleteMedia(uris.toList())
    }
}

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

    suspend fun priorFolders(context: Context): List<CatalogFolderRow> = withContext(Dispatchers.IO) {
        CatalogDatabase.get(context).catalog().allFolders()
    }

    suspend fun priorVolumes(context: Context): Map<String, CatalogVolumeRow> = withContext(Dispatchers.IO) {
        CatalogDatabase.get(context).catalog().allVolumes().associateBy { it.storageId }
    }

    suspend fun captureScanStart(context: Context): Map<String, CatalogVolumeRow> =
        withContext(Dispatchers.IO) {
            runCatching { MediaStore.getExternalVolumeNames(context) }.getOrDefault(emptySet()).associateWith { volume ->
                CatalogVolumeRow(
                    storageId = volume,
                    displayName = volume,
                    mediaStoreGeneration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        runCatching { MediaStore.getGeneration(context, volume) }.getOrDefault(0L) else 0L,
                    mediaStoreVersion = runCatching { MediaStore.getVersion(context, volume) }.getOrDefault(""),
                    lastScannedMillis = System.currentTimeMillis(),
                    connected = true,
                )
            }
        }

    suspend fun persist(context: Context, snapshot: LibrarySnapshot.Value,
        atScanStart: Map<String, CatalogVolumeRow> = emptyMap()) = withContext(Dispatchers.IO) {
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
        // Capture MediaStore counters *before* scanning, never afterward:
        // otherwise a file changed mid-scan could be silently missed.
        val activeVolumes = snapshot.items.groupBy { it.storageId }.map { (id, items) ->
            val start = atScanStart[id]
            CatalogVolumeRow(
                storageId = id,
                displayName = items.first().storageName,
                mediaStoreGeneration = start?.mediaStoreGeneration ?: 0L,
                mediaStoreVersion = start?.mediaStoreVersion.orEmpty(),
                lastScannedMillis = now,
                connected = true,
            )
        }
        val activeIds = activeVolumes.mapTo(hashSetOf()) { it.storageId }
        // Keep track of disconnected volumes rather than silently forgetting
        // their identities when a USB drive is unplugged.
        val disconnected = CatalogDatabase.get(context).catalog().allVolumes()
            .filterNot { it.storageId in activeIds }.map { it.copy(connected = false) }
        val volumes = disconnected + activeVolumes
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

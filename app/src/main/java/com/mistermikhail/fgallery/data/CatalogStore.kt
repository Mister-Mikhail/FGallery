package com.mistermikhail.fgallery.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
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
    private const val LAST_FULL_SCAN = "last_full_safety_scan"

    fun fullScanDue(context: Context): Boolean {
        val previous = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(LAST_FULL_SCAN, 0L)
        return System.currentTimeMillis() - previous >= 24L * 60L * 60L * 1000L
    }

    fun recordFullScan(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(LAST_FULL_SCAN, System.currentTimeMillis()).apply()
    }

    suspend fun restore(context: Context, hasPermission: Boolean): LibrarySnapshot.Value? =
        withContext(Dispatchers.IO) {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!preferences.contains(PERMISSION_KEY) ||
                preferences.getBoolean(PERMISSION_KEY, false) != hasPermission) return@withContext null
            val dao = CatalogDatabase.get(context).catalog()
            // Keep disconnected-drive rows on disk, but do not show inaccessible
            // photos in the active gallery until the volume is mounted/scanned.
            val disconnectedIds = dao.allVolumes().asSequence()
                .filterNot { it.connected }.mapTo(hashSetOf()) { it.storageId }
            // PERMISSION_KEY is written only after a successful Room sync,
            // so an empty Room table is a valid empty gallery, not a migration
            // failure. Falling back to older JSON would resurrect deleted media.
            val allRows = dao.allMedia()
            // If all cached items are offline, likewise return an intentionally
            // empty visible snapshot instead of resurrecting them from JSON.
            val rows = allRows.filterNot { it.storageId in disconnectedIds }
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

    /** MediaStore can omit an empty USB/SD volume even while it is mounted.
     * Keep mounted volumes in the checkpoint inventory so an empty but
     * connected drive is never mistaken for an unplugged one.
     */
    internal fun scanStartVolumeIds(
        mediaStoreIds: Set<String>,
        mountedIds: Set<String>,
    ): Set<String> = mediaStoreIds + mountedIds

    suspend fun captureScanStart(context: Context): Map<String, CatalogVolumeRow> =
        withContext(Dispatchers.IO) {
            val indexed = runCatching { MediaStore.getExternalVolumeNames(context) }
                .getOrDefault(emptySet())
            val mounted = StorageAccess.mounted(context).associateBy { it.id }
            scanStartVolumeIds(indexed, mounted.keys).associateWith { volume ->
                CatalogVolumeRow(
                    storageId = volume,
                    displayName = mounted[volume]?.name ?: volume,
                    mediaStoreGeneration = if (volume in indexed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        runCatching { MediaStore.getGeneration(context, volume) }.getOrDefault(0L) else 0L,
                    mediaStoreVersion = if (volume in indexed)
                        runCatching { MediaStore.getVersion(context, volume) }.getOrDefault("") else "",
                    lastScannedMillis = System.currentTimeMillis(),
                    connected = true,
                )
            }
        }

    suspend fun persist(context: Context, snapshot: LibrarySnapshot.Value,
        atScanStart: Map<String, CatalogVolumeRow> = emptyMap()) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val media = snapshot.items.map { item -> toRow(item, item.uriKey in snapshot.bin) }
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
        val activeVolumes = activeVolumeRows(
            snapshot.items.associate { it.storageId to it.storageName },
            atScanStart,
            now,
        )
        val activeIds = activeVolumes.mapTo(hashSetOf()) { it.storageId }
        // Keep track of disconnected volumes rather than silently forgetting
        // their identities when a USB drive is unplugged.
        val disconnected = CatalogDatabase.get(context).catalog().allVolumes()
            .filterNot { it.storageId in activeIds }.map { it.copy(connected = false) }
        val volumes = disconnected + activeVolumes
        // Room transaction either commits the entire snapshot or leaves the
        // prior catalogue unchanged. Never use destructive fallback migrations.
        CatalogDatabase.get(context).catalog().synchronizeSnapshot(media, folders, volumes)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(PERMISSION_KEY, snapshot.permission).apply()
    }

    /**
     * A mounted, empty MediaStore volume is still connected. Previously volumes
     * were derived only from discovered files; deleting the last photo made a
     * mounted volume look disconnected and lost its generation checkpoint.
     * Keep the scan-start inventory even when it has zero matching media.
     */
    internal fun activeVolumeRows(
        namesFromMedia: Map<String, String>,
        atScanStart: Map<String, CatalogVolumeRow>,
        now: Long,
    ): List<CatalogVolumeRow> = (atScanStart.keys + namesFromMedia.keys).map { id ->
        val start = atScanStart[id]
        CatalogVolumeRow(
            storageId = id,
            displayName = namesFromMedia[id] ?: start?.displayName ?: id,
            mediaStoreGeneration = start?.mediaStoreGeneration ?: 0L,
            mediaStoreVersion = start?.mediaStoreVersion.orEmpty(),
            lastScannedMillis = now,
            connected = true,
        )
    }

    private fun toRow(item: MediaItem, recycled: Boolean): CatalogMediaRow =
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
            recycled = recycled,
        )

    /**
     * Bounded, resumable Stage 6 exact-copy fingerprinting. Only accessible
     * local files and seekable, versioned content URIs are hashed. Providers
     * without reliable metadata or seeking are safely skipped. Never read media on the UI thread.
     * A matching edge hash is not evidence of duplication: only full SHA-256
     * groups may be offered to the user (no deletion occurs here).
     */
    suspend fun indexExactDuplicateCandidates(
        context: Context,
        canWork: () -> Boolean,
        quickBudget: Int = 12,
        fullBudget: Int = 2,
    ): Boolean = withContext(Dispatchers.IO) {
        val dao = CatalogDatabase.get(context).catalog()
        var advanced = false
        val job = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        fun ensureIdle() {
            job?.ensureActive()
            if (!canWork()) throw kotlinx.coroutines.CancellationException("Foreground media interaction")
        }
        val provider = ContentFingerprintSource(context)
        fun localFile(row: CatalogMediaRow): File? = row.sourcePath
            .takeIf { it.isNotBlank() && it.startsWith("/") }
            ?.let(::File)?.takeIf { it.isFile && it.canRead() && it.length() == row.sizeBytes }
        fun candidateAccessible(row: CatalogMediaRow): Boolean =
            localFile(row) != null || Uri.parse(row.uri).scheme == "content"

        fun stableRead(row: CatalogMediaRow, quick: Boolean): String? {
            ensureIdle()
            return try {
                val file = localFile(row)
                if (file != null) {
                    val beforeSize = file.length()
                    val beforeModified = file.lastModified()
                    val digest = if (quick) DuplicateFingerprints.quick(file, ::ensureIdle)
                        else DuplicateFingerprints.full(file, ::ensureIdle)
                    ensureIdle()
                    digest.takeIf {
                        file.length() == beforeSize && file.lastModified() == beforeModified
                    }
                } else {
                    val uri = Uri.parse(row.uri)
                    if (uri.scheme != "content") return null
                    val before = provider.signature(uri) ?: return null
                    if (before.size != row.sizeBytes) return null
                    val digest = if (quick) provider.quick(uri, row.sizeBytes, ::ensureIdle)
                        else provider.full(uri, row.sizeBytes, ::ensureIdle)
                    ensureIdle()
                    digest?.takeIf { before == provider.signature(uri) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Permission loss, provider failure, disappearing file, or
                // non-seekable SAF pipe: do not write a speculative digest.
                null
            }
        }
        for (row in dao.pendingQuickHashCandidates().asSequence().filter(::candidateAccessible).take(quickBudget)) {
            ensureIdle()
            stableRead(row, true)?.let { digest ->
                if (dao.setQuickHash(row.uri, row.sizeBytes, row.dateModifiedMillis, digest) > 0) advanced = true
            }
        }
        for (row in dao.pendingFullHashCandidates().asSequence().filter(::candidateAccessible).take(fullBudget)) {
            ensureIdle()
            // Verify the cached edge fingerprint before trusting the grouping.
            val edge = stableRead(row, true) ?: continue
            if (edge != row.quickHash) {
                if (dao.setQuickHash(row.uri, row.sizeBytes, row.dateModifiedMillis, edge) > 0) advanced = true
                continue
            }
            val digest = stableRead(row, false) ?: continue
            // A provider may replace a document without changing coarse mtime.
            // Check the edge again after the expensive streaming full read.
            val afterEdge = stableRead(row, true) ?: continue
            if (afterEdge != edge) {
                if (dao.setQuickHash(row.uri, row.sizeBytes, row.dateModifiedMillis, afterEdge) > 0) advanced = true
                continue
            }
            if (dao.setFullHash(row.uri, row.sizeBytes, row.dateModifiedMillis, edge, digest) > 0) advanced = true
        }
        // Signal the idle scheduler to keep draining finite batches. A false
        // result means no accessible candidate advanced, so stop rather than
        // spin forever on unreadable/unchanged provider rows.
        advanced
    }

    /** Stage 6: bounded idle pass over EXISTING JPEG/PNG posters. Never start
     * a RAW decode or request a new thumbnail. Keyset paging avoids starving
     * later files when the first posters are missing or evicted. */
    suspend fun indexVisualCandidateHashes(
        context: Context,
        canWork: () -> Boolean,
        afterUri: String = "",
        budget: Int = 48,
    ): String? = withContext(Dispatchers.IO) {
        val dao = CatalogDatabase.get(context).catalog()
        val job = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        fun ensureIdle() {
            job?.ensureActive()
            if (!canWork()) throw kotlinx.coroutines.CancellationException("Foreground media interaction")
        }
        val candidates = dao.pendingVisualHashCandidates(afterUri, budget)
        for (row in candidates) {
            ensureIdle()
            try {
                val hash = VisualThumbnailFingerprintSource.cachedDifferenceHash(
                    context, row, ::ensureIdle,
                ) ?: continue
                ensureIdle()
                dao.setVisualHash(row.uri, row.sizeBytes, row.dateModifiedMillis,
                    row.quickHash, hash)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Evicted/corrupt cached poster: leave the row unmodified.
            }
        }
        candidates.lastOrNull()?.uri
    }

    /** Keep Room current immediately after a successful app-owned file creation.
     * The next MediaStore/SAF reconciliation remains authoritative. */
    suspend fun upsertItems(context: Context, items: List<MediaItem>) = withContext(Dispatchers.IO) {
        if (items.isNotEmpty()) CatalogDatabase.get(context).catalog()
            .applyMediaChanges(items.map { toRow(it, false) }, emptyList())
    }

    /** Remove old URI identities after a completed move or permanent deletion. */
    suspend fun removeUris(context: Context, uris: Set<String>) = withContext(Dispatchers.IO) {
        if (uris.isNotEmpty()) CatalogDatabase.get(context).catalog()
            .applyMediaChanges(emptyList(), uris)
    }
}

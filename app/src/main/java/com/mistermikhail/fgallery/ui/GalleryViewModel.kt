package com.mistermikhail.fgallery.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mistermikhail.fgallery.data.LibrarySnapshot
import com.mistermikhail.fgallery.data.CatalogStore
import com.mistermikhail.fgallery.data.CatalogMediaRow
import com.mistermikhail.fgallery.data.DuplicateSearchInput
import com.mistermikhail.fgallery.data.VisualDuplicateSourceVerification
import com.mistermikhail.fgallery.data.DriveLibrary
import com.mistermikhail.fgallery.data.DriveRecycleBin
import com.mistermikhail.fgallery.data.RecycleBinNotifications
import com.mistermikhail.fgallery.data.DocumentLibrary
import com.mistermikhail.fgallery.data.AppSettingsRepository
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.MediaRepository
import com.mistermikhail.fgallery.data.StorageAccess
import com.mistermikhail.fgallery.data.ThumbnailCache
import com.mistermikhail.fgallery.data.comparisonTrashSha256
import com.mistermikhail.fgallery.data.trashExactDuplicateBatch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Album date is the latest timestamp of a contained file, not the folder mtime. */
internal fun albumFileTimestamp(capturedOrCreated: Long, modified: Long): Long =
    maxOf(capturedOrCreated, modified)

enum class MediaFilter { ALL, PHOTOS, VIDEOS, RAW }
enum class GridMode { MOSAIC, UNIFORM }
enum class SortMode {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    SIZE_ASC,
    TYPE_ASC,
    TYPE_DESC,
}

data class AlbumSummary(
    val name: String,
    val cover: MediaItem,
    val count: Int,
    val newestDateMillis: Long,
    val totalSizeBytes: Long,
    val key: String = name,
)

data class GalleryUiState(
    val allItems: List<MediaItem> = emptyList(),
    val visibleItems: List<MediaItem> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val recycleBinItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasPermission: Boolean = false,
    val query: String = "",
    val searchVisible: Boolean = false,
    val filter: MediaFilter = MediaFilter.ALL,
    val gridMode: GridMode = GridMode.MOSAIC,
    val sortMode: SortMode = SortMode.DATE_DESC,
    val selectedAlbum: String? = null,
    val quickExifEnabled: Boolean = true,
    val confirmMove: Boolean = false,
    val confirmRename: Boolean = false,
    val settingsVisible: Boolean = false,
    val recycleBinVisible: Boolean = false,
    val recycleBinUris: Set<String> = emptySet(),
    val physicalBinUris: Set<String> = emptySet(),
    val pendingViewerTrash: Set<String> = emptySet(),
)

class GalleryViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    private val driveBin = DriveRecycleBin(application)
    private val binNotifications = RecycleBinNotifications(application)
    private val pendingBinIndexMoves = ConcurrentHashMap<String, MediaItem>()
    private var lastJsonBackupMillis: Long = 0L
    private var thumbnailJob: kotlinx.coroutines.Job? = null
    private var albumWarmJob: kotlinx.coroutines.Job? = null
    private var refreshJob: kotlinx.coroutines.Job? = null
    private var fingerprintJob: kotlinx.coroutines.Job? = null
    @Volatile private var duplicateSearchActive = false
    private var libraryChangedDuringDuplicateSearch = false
    private val activeBinMoves = MutableStateFlow(0)
    private val viewerTrashQueue = ArrayDeque<MediaItem>()
    private var viewerTrashJob: kotlinx.coroutines.Job? = null
    private val viewerTrashErrors = Channel<String>(Channel.UNLIMITED)
    internal val viewerTrashFailures = viewerTrashErrors.receiveAsFlow()
    private val catalogueWrites = Mutex()
    private var binIndexJob: kotlinx.coroutines.Job? = null
    private var observingCleanupViewer = false
    private val cleanupMediaPaths = ConcurrentHashMap.newKeySet<String>()
    private var deferredObservedRefresh = false
    private var deferredExplicitRefresh: Boolean? = null
    @Volatile private var lastCatalogueScanSucceeded: Boolean? = null

    internal val duplicateSearch by lazy {
        DuplicateSearchController(
            scope = viewModelScope,
            load = ::loadDuplicateSearch,
            verifyVisual = { first, second ->
                VisualDuplicateSourceVerification.verify(getApplication(), first, second)
            },
            trash = ::trashConfirmedExactDuplicates,
            trashVisual = ::trashVisualDuplicates,
            prepareVisual = ::prepareDuplicateVisualRows,
        )
    }

    internal fun openDuplicateSearch() {
        if (duplicateSearchActive) return
        duplicateSearchActive = true
        duplicateSearch.refresh(force = false)
    }

    internal fun closeDuplicateSearch() {
        duplicateSearch.close()
        duplicateSearchActive = false
        if (libraryChangedDuringDuplicateSearch) {
            libraryChangedDuringDuplicateSearch = false
            refresh()
        }
    }

    internal fun onObservedLibraryChange() {
        when {
            activeBinMoves.value > 0 || observingCleanupViewer -> deferredObservedRefresh = true
            duplicateSearchActive -> libraryChangedDuringDuplicateSearch = true
            else -> refresh()
        }
    }

    internal fun onCleanupViewerChanged(active: Boolean) {
        observingCleanupViewer = active
        if (!active) resumeDeferredRefresh()
    }

    private fun resumeDeferredRefresh() {
        if (activeBinMoves.value != 0 || _uiState.value.pendingViewerTrash.isNotEmpty()) return
        if (!observingCleanupViewer && cleanupMediaPaths.isNotEmpty()) {
            val paths = cleanupMediaPaths.toSet()
            cleanupMediaPaths.removeAll(paths)
            viewModelScope.launch(Dispatchers.IO) { driveBin.reconcileAndroidIndex(paths) }
        }
        val explicit = deferredExplicitRefresh
        if (explicit != null) {
            deferredExplicitRefresh = null
            deferredObservedRefresh = false
            refresh(forceFull = explicit)
        } else if (!observingCleanupViewer && deferredObservedRefresh) {
            deferredObservedRefresh = false
            onObservedLibraryChange()
        }
    }

    internal suspend fun isOwnRecycleBinMediaChange(uri: Uri, flags: Int): Boolean =
        binNotifications.isOwnMediaChange(uri, flags)

    internal suspend fun awaitRecycleBinMoves() {
        uiState.first { it.pendingViewerTrash.isEmpty() }
        activeBinMoves.first { it == 0 }
    }

    internal fun isOwnRecycleBinFileChange(folder: String, event: Int, path: String?): Boolean =
        binNotifications.isOwnFileChange(folder, event, path)

    private suspend fun loadDuplicateSearch(
        force: Boolean,
        progress: (String) -> Unit,
    ): DuplicateSearchInput {
        awaitRecycleBinMoves()
        progress("Сканируем доступные носители…")
        if (force || refreshJob == null || (lastCatalogueScanSucceeded != true && refreshJob?.isActive == false)) {
            refresh(forceFull = force)
        }
        while (true) {
            val current = refreshJob ?: break
            current.join()
            if (refreshJob === current) break
        }
        check(lastCatalogueScanSucceeded == true) { "Не удалось обновить каталог носителей. Повторите поиск." }
        fingerprintJob?.cancelAndJoin()
        val context = getApplication<Application>()
        flushPendingBinIndexMoves()
        suspend fun searchInput() = CatalogStore.duplicateSearchInput(context, _uiState.value.hasPermission).let { input ->
            if (pendingBinIndexMoves.isEmpty()) input
            else input.copy(rows = input.rows.filterNot { pendingBinIndexMoves.containsKey(it.uri) })
        }
        val input = searchInput()
        if (input.pendingFingerprints == 0) return input
        progress("Файлов в очереди проверки: ${input.pendingFingerprints}")
        var batches = 0
        while (CatalogStore.indexExactDuplicateCandidates(
                context, canWork = { true }, quickBudget = 48, fullBudget = 8,
            )
        ) {
            batches++
            if (batches % 4 == 0) progress("Проверяем точные копии · обработано пакетов: $batches")
        }
        return searchInput()
    }

    internal fun onDuplicateStorageChanged() {
        binNotifications.clear()
        if (duplicateSearchActive) duplicateSearch.refresh(force = false)
    }

    private suspend fun prepareDuplicateVisualRows(rows: List<CatalogMediaRow>) =
        withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            var cursor = ""
            while (true) {
                cursor = CatalogStore.indexVisualCandidateHashes(context, canWork = { true }, afterUri = cursor)
                    ?: break
            }
            val current = CatalogStore.exactDuplicateReviewRows(context).associateBy { it.uri }
            rows.map { row ->
                val updated = current[row.uri]
                if (updated != null && row.sizeBytes == updated.sizeBytes &&
                    row.dateModifiedMillis == updated.dateModifiedMillis && row.quickHash == updated.quickHash
                ) row.copy(perceptualHash = updated.perceptualHash) else row
            }
        }

    private val repository = MediaRepository(application)
    private val settingsRepository = AppSettingsRepository(application)
    private val _uiState = MutableStateFlow(LibrarySnapshot.memory?.let {
        derive(GalleryUiState(allItems = it.items, physicalBinUris = it.bin, hasPermission = it.permission, selectedAlbum = savedStateHandle["selected_album"]))
    } ?: GalleryUiState(selectedAlbum = savedStateHandle["selected_album"]))
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()
    private var snapshotRestored = false

    init {
        viewModelScope.launch {
            settingsRepository.confirmMove.collect { value ->
                _uiState.update { it.copy(confirmMove = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.confirmRename.collect { value ->
                _uiState.update { it.copy(confirmRename = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.quickExifEnabled.collect { enabled ->
                _uiState.update { it.copy(quickExifEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            settingsRepository.sortModeName.collect { saved ->
                val mode = runCatching {
                    SortMode.valueOf(saved)
                }.getOrDefault(SortMode.DATE_DESC)

                _uiState.update { current ->
                    derive(current.copy(sortMode = mode))
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.recycleBinUris.collect { uris ->
                _uiState.update { current ->
                    derive(current.copy(recycleBinUris = uris))
                }
            }
        }
    }

    /**
     * Bulk prewarming of RAW without an embedded JPEG can trigger a native
     * demosaic of a 100 MP file before the user ever opens its folder.
     * Keep the first album cover in the priority queue; other RAW thumbnails
     * are requested by visible Coil tiles instead of consuming idle memory.
     */
    internal fun bulkPrewarmEligible(kind: MediaKind): Boolean = kind != MediaKind.RAW

    // Stable album identity must include drive and exact folder, never just a leaf name.
    private fun albumKey(item: MediaItem): String = "${item.storageId}:${item.folderTarget.ifBlank { item.relativePath.ifBlank { item.album } }}"

    private fun mediaOrder(mode: SortMode): Comparator<MediaItem> =
        when (mode) {
            SortMode.DATE_DESC -> compareByDescending<MediaItem> { it.dateTakenMillis }.thenBy { it.name.lowercase() }
            SortMode.DATE_ASC -> compareBy<MediaItem> { it.dateTakenMillis }.thenBy { it.name.lowercase() }
            SortMode.NAME_ASC -> compareBy { it.name.lowercase() }
            SortMode.NAME_DESC -> compareByDescending { it.name.lowercase() }
            SortMode.SIZE_DESC -> compareByDescending { it.sizeBytes }
            SortMode.SIZE_ASC -> compareBy { it.sizeBytes }
            SortMode.TYPE_ASC -> compareBy<MediaItem> { it.kind.name }.thenBy { it.name.lowercase() }
            SortMode.TYPE_DESC -> compareByDescending<MediaItem> { it.kind.name }.thenBy { it.name.lowercase() }
        }

    private fun matchesFilter(item: MediaItem, filter: MediaFilter): Boolean = when (filter) {
        MediaFilter.ALL -> true
        MediaFilter.PHOTOS -> item.kind == MediaKind.IMAGE
        MediaFilter.VIDEOS -> item.kind == MediaKind.VIDEO
        MediaFilter.RAW -> item.kind == MediaKind.RAW
    }

    private fun albumSummary(
        key: String,
        items: List<MediaItem>,
        query: String,
        order: Comparator<MediaItem>,
    ): AlbumSummary? {
        val name = items.first().album
        if (query.isNotBlank() && !name.contains(query, ignoreCase = true)) return null
        return AlbumSummary(
            name = name,
            key = key,
            cover = checkNotNull(items.minWithOrNull(order)),
            count = items.size,
            newestDateMillis = items.maxOf { albumFileTimestamp(it.dateTakenMillis, it.dateModifiedMillis) },
            totalSizeBytes = items.sumOf { it.sizeBytes },
        )
    }

    private fun sortAlbums(albums: List<AlbumSummary>, mode: SortMode): List<AlbumSummary> = when (mode) {
        SortMode.DATE_DESC -> albums.sortedByDescending { it.newestDateMillis }
        SortMode.DATE_ASC -> albums.sortedBy { it.newestDateMillis }
        SortMode.NAME_ASC -> albums.sortedBy { it.name.lowercase() }
        SortMode.NAME_DESC -> albums.sortedByDescending { it.name.lowercase() }
        SortMode.SIZE_ASC -> albums.sortedBy { it.totalSizeBytes }
        SortMode.SIZE_DESC -> albums.sortedByDescending { it.totalSizeBytes }
        SortMode.TYPE_ASC -> albums.sortedBy { it.cover.kind.name }
        SortMode.TYPE_DESC -> albums.sortedByDescending { it.cover.kind.name }
    }

    private fun derive(state: GalleryUiState): GalleryUiState {
        val binUris = state.recycleBinUris + state.physicalBinUris
        val order = mediaOrder(state.sortMode)
        val available = state.allItems.filter { it.uriKey !in binUris && matchesFilter(it, state.filter) }
        val visible = available.asSequence()
            .filter { state.selectedAlbum == null || albumKey(it) == state.selectedAlbum }
            .filter { state.query.isBlank() || it.name.contains(state.query, ignoreCase = true) }
            .sortedWith(order)
            .toList()
        val albums = available.groupBy(::albumKey).mapNotNull { (key, items) ->
            albumSummary(key, items, state.query, order)
        }
        return state.copy(
            visibleItems = visible,
            recycleBinItems = state.allItems.filter { it.uriKey in binUris }.sortedWith(order),
            albums = sortAlbums(albums, state.sortMode),
        )
    }

    private fun applyBinMoves(state: GalleryUiState, moved: Map<String, MediaItem>): GalleryUiState {
        val destinations = moved.values.mapTo(hashSetOf()) { it.uriKey }
        val retired = moved.keys + destinations
        val bin = state.physicalBinUris + destinations
        val affectedAlbums = hashSetOf<String>()
        val remaining = state.allItems.filter { item ->
            if (item.uriKey in retired) {
                affectedAlbums += albumKey(item)
                false
            } else true
        }
        val order = mediaOrder(state.sortMode)
        val revised = if (affectedAlbums.isEmpty()) emptyList() else remaining.asSequence()
            .filter { it.uriKey !in bin && it.uriKey !in state.recycleBinUris && matchesFilter(it, state.filter) }
            .filter { albumKey(it) in affectedAlbums }
            .groupBy(::albumKey)
            .mapNotNull { (key, items) -> albumSummary(key, items, state.query, order) }
        return state.copy(
            allItems = remaining + moved.values,
            visibleItems = state.visibleItems.filterNot { it.uriKey in retired },
            recycleBinItems = (state.recycleBinItems.filterNot { it.uriKey in retired } + moved.values).sortedWith(order),
            physicalBinUris = bin,
            albums = if (affectedAlbums.isEmpty()) state.albums else
                sortAlbums(state.albums.filterNot { it.key in affectedAlbums } + revised, state.sortMode),
        )
    }

    /** Insert a successful edited copy immediately, rather than waiting for Android's media scanner. */
    suspend fun onSavedCopy(source: MediaItem, uri: android.net.Uri, mime: String) {
        val (filename, size, modified) = withContext(Dispatchers.IO) {
            if (uri.scheme == "file") {
                val file = java.io.File(uri.path.orEmpty())
                Triple(file.name, file.length(), file.lastModified())
            } else {
                val context = getApplication<Application>()
                val nameColumn = android.provider.OpenableColumns.DISPLAY_NAME
                val sizeColumn = android.provider.OpenableColumns.SIZE
                context.contentResolver.query(uri, arrayOf(nameColumn, sizeColumn), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) Triple(cursor.getString(0) ?: source.name, cursor.getLong(1), System.currentTimeMillis())
                    else null
                } ?: Triple(source.name, 0L, System.currentTimeMillis())
            }
        }
        val copy = source.copy(
            id = com.mistermikhail.fgallery.data.StorageAccess.stableId(uri.toString()),
            uri = uri,
            name = filename,
            mimeType = mime,
            kind = if (mime.startsWith("video/")) MediaKind.VIDEO else MediaKind.IMAGE,
            sizeBytes = size,
            dateModifiedMillis = modified,
            sourcePath = if (uri.scheme == "file") uri.path.orEmpty() else "",
        )
        _uiState.update { current ->
            derive(current.copy(
                allItems = current.allItems.filterNot { it.uriKey == copy.uriKey } + copy,
                filter = if (current.filter == MediaFilter.RAW && source.kind == MediaKind.RAW) MediaFilter.ALL else current.filter,
            ))
        }
        viewModelScope.launch {
            // A successful edit is visible in the durable catalogue before
            // Android's asynchronous MediaStore scanner notices the new URI.
            try { CatalogStore.upsertItems(getApplication(), listOf(copy)) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* A full scan below remains the fallback. */ }
            delay(900L) // Allow MediaStore.scanFile to include the copy before reconciling the catalogue.
            refresh()
        }
    }

    fun onPermissionChanged(granted: Boolean) {
        _uiState.update { current ->
            if (granted != current.hasPermission) derive(current.copy(hasPermission = granted, allItems = emptyList()))
            else current.copy(hasPermission = granted)
        }
        refresh()
    }

    fun refresh(forceFull: Boolean = false) {
        if (activeBinMoves.value > 0 || _uiState.value.pendingViewerTrash.isNotEmpty()) {
            deferredExplicitRefresh = forceFull || deferredExplicitRefresh == true
            return
        }
        fingerprintJob?.cancel()
        refreshJob?.cancel()
        val restoreSnapshot = !snapshotRestored
        snapshotRestored = true
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            if (restoreSnapshot && _uiState.value.allItems.isEmpty()) {
                (try {
                    CatalogStore.restore(getApplication(), _uiState.value.hasPermission)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                  catch (_: Exception) { null }
                    ?: LibrarySnapshot.load(getApplication(), _uiState.value.hasPermission))?.let { saved ->
                    withContext(Dispatchers.Main) {
                        _uiState.update { derive(it.copy(allItems = saved.items, physicalBinUris = saved.bin)) }
                    }
                }
            }
            _uiState.update { it.copy(isLoading = it.allItems.isEmpty()) }

            // The grid stays usable from Room while a daily/explicit full
            // safety reconciliation runs on the I/O dispatcher.
            val fullSafetyScan = forceFull || CatalogStore.fullScanDue(getApplication())
            // Capture generations BEFORE scanning, so mid-scan changes cannot vanish.
            val started = try { CatalogStore.captureScanStart(getApplication()) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { emptyMap() }
            var scanSucceeded = true
            val normal = try {
                val prior = try { CatalogStore.priorVolumes(getApplication()) }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { emptyMap() }
                val media = if (_uiState.value.hasPermission)
                    repository.loadIncremental(_uiState.value.allItems, prior, fullSafetyScan) else emptyList()
                val documents = DocumentLibrary(getApplication()).load()
                val folderSnapshot = if (fullSafetyScan) emptyList()
                    else try { CatalogStore.priorFolders(getApplication()) }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { emptyList() }
                (media + documents + DriveLibrary.load(
                    getApplication(), media + documents,
                    if (fullSafetyScan) emptyList() else _uiState.value.allItems,
                    folderSnapshot,
                )).distinctBy { it.uriKey }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
                // Preserve the last good Room/JSON checkpoint if a provider
                // fails. A failed scan must never be committed as an empty
                // library or advance MediaStore generation counters.
                scanSucceeded = false
                _uiState.value.allItems.filterNot { it.uriKey in _uiState.value.physicalBinUris }
            }
            val recycled = driveBin.load()
            val items = normal + recycled

            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    derive(
                        current.copy(
                            allItems = items,
                            physicalBinUris = recycled.mapTo(mutableSetOf()) { it.uriKey },
                            isLoading = false,
                        )
                    )
                }
            }

            val snapshot = LibrarySnapshot.Value(items, recycled.mapTo(hashSetOf()) { it.uriKey }, _uiState.value.hasPermission)
            // A newer refresh cancels this one. Never turn cancellation into a
            // successful checkpoint or continue writing stale JSON/thumbnail jobs.
            val roomStored = if (!scanSucceeded) false else try {
                catalogueWrites.withLock {
                    currentCoroutineContext().ensureActive()
                    CatalogStore.persist(getApplication(), snapshot, started)
                }
                true
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
              catch (_: Exception) { false }
            lastCatalogueScanSucceeded = roomStored
            if (roomStored && fullSafetyScan) CatalogStore.recordFullScan(getApplication())
            if (roomStored && !duplicateSearchActive) {
                // Fingerprint only potential exact-copy candidates, with small
                // per-refresh budgets. Never compete with scrolling or viewing.
                fingerprintJob = viewModelScope.launch(Dispatchers.IO) {
                    delay(8_000L)
                    var waits = 0
                    while (!ThumbnailCache.canRunBackgroundIndexing() && waits++ < 40) delay(1_500L)
                    if (ThumbnailCache.canRunBackgroundIndexing()) {
                        try {
                            // Compute cheap dHash candidates only from already
                            // cached ordinary image posters. No RAW decode and
                            // no visual duplicate proposals until strict pixel
                            // verification rejects edits, crops and burst shots.
                            var lastVisualUri = ""
                            for (page in 0 until 96) {
                                if (!ThumbnailCache.canRunBackgroundIndexing()) return@launch
                                val next = CatalogStore.indexVisualCandidateHashes(
                                    getApplication(), ThumbnailCache::canRunBackgroundIndexing,
                                    afterUri = lastVisualUri,
                                ) ?: break
                                lastVisualUri = next
                                delay(350L)
                            }
                            // One refresh may expose hundreds of same-size files.
                            // Drain small bounded batches while idle, rather than
                            // indexing only 12 candidates until the NEXT refresh.
                            // Never compete with a visible tile or RAW viewer.
                            repeat(48) {
                                if (!ThumbnailCache.canRunBackgroundIndexing()) return@launch
                                val progressed = CatalogStore.indexExactDuplicateCandidates(
                                    getApplication(), ThumbnailCache::canRunBackgroundIndexing)
                                if (!progressed) return@launch
                                delay(3_000L)
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // Foreground interaction or a newer scan supersedes the work.
                            if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw e
                        } catch (_: Exception) {
                            // Fingerprints are optional; media and the catalogue stay intact.
                        }
                    }
                }
            }
            // A failed scan must not replace the previous JSON fallback either.
            // Leave the last good checkpoint intact for the next retry.
            if (scanSucceeded) {
                val now = System.currentTimeMillis()
                if (!roomStored || restoreSnapshot || now - lastJsonBackupMillis >= 5 * 60 * 1000L) {
                    try {
                        LibrarySnapshot.save(getApplication(), snapshot)
                        lastJsonBackupMillis = now
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                      catch (_: Exception) { /* Room remains the source of truth. */ }
                } else LibrarySnapshot.remember(snapshot)
            }
            thumbnailJob?.cancel()
            // Match the actual on-screen covers (folder + volume + active sort).
            // Grouping by leaf album name used to skip covers of same-named
            // folders on different drives and generated the wrong prewarm queue.
            val albumCovers = _uiState.value.albums.map { it.cover }

            thumbnailJob = viewModelScope.launch(Dispatchers.IO) {
                // Cache generation must never drive Compose recomposition.
                preloadThumbnailBatches(
                    items = albumCovers,
                    batchSize = 12,
                    highQuality = false,
                    pauseBetweenBatchesMs = 0L,
                )

                val coverIds = albumCovers.mapTo(hashSetOf()) { it.id }
                val remaining = items.filterNot { it.id in coverIds || !bulkPrewarmEligible(it.kind) }

                preloadThumbnailBatches(
                    items = remaining,
                    batchSize = 18,
                    highQuality = false,
                    pauseBetweenBatchesMs = 45L,
                )
                preloadThumbnailBatches(
                    items = albumCovers.filter { bulkPrewarmEligible(it.kind) },
                    batchSize = 8,
                    highQuality = true,
                    pauseBetweenBatchesMs = 40L,
                )

            }
        }
    }

    private suspend fun preloadThumbnailBatches(
        items: List<MediaItem>,
        batchSize: Int,
        highQuality: Boolean,
        pauseBetweenBatchesMs: Long,
    ) {
        val batches = items.chunked(batchSize)

        batches.forEachIndexed { index, batch ->
            ThumbnailCache.preload(
                context = getApplication<Application>(),
                items = batch,
                highQuality = highQuality,
            )

            if (pauseBetweenBatchesMs > 0L && index < batches.lastIndex) {
                delay(pauseBetweenBatchesMs)
            }
        }
    }

    private fun prewarmAlbum(name: String) {
        val snapshot = _uiState.value
        val items = snapshot.allItems.filter {
            albumKey(it) == name && it.uriKey !in (snapshot.recycleBinUris + snapshot.physicalBinUris) &&
                bulkPrewarmEligible(it.kind)
        }

        albumWarmJob?.cancel()
        albumWarmJob = viewModelScope.launch(Dispatchers.IO) {
            // No StateFlow updates here: creating cache files must not invalidate
            // the entire gallery while the user is scrolling.
            preloadThumbnailBatches(
                items = items,
                batchSize = 12,
                highQuality = false,
                pauseBetweenBatchesMs = 25L,
            )

            delay(350L)

            preloadThumbnailBatches(
                items = items.take(24),
                batchSize = 6,
                highQuality = true,
                pauseBetweenBatchesMs = 80L,
            )
        }
    }

    internal fun enqueueViewerTrash(item: MediaItem): Boolean {
        val uri = item.uriKey
        val state = _uiState.value
        if (uri in state.pendingViewerTrash || uri in state.recycleBinUris || uri in state.physicalBinUris) return false
        // Hide the source in the viewer while the existing bin journal protects the physical move.
        viewerTrashQueue.addLast(item)
        _uiState.update { it.copy(pendingViewerTrash = it.pendingViewerTrash + uri) }
        if (viewerTrashJob?.isActive != true) {
            viewerTrashJob = viewModelScope.launch {
                while (viewerTrashQueue.isNotEmpty()) {
                    moveQueuedViewerItem(viewerTrashQueue.removeFirst())
                }
                resumeDeferredRefresh()
            }
        }
        return true
    }

    private suspend fun moveQueuedViewerItem(item: MediaItem) {
        try {
            moveItemsToRecycleBin(listOf(item), deferMediaScan = true) { source ->
                val file = StorageAccess.file(getApplication(), source)
                file == null || (
                    file.isFile &&
                        (source.sizeBytes <= 0L || file.length() == source.sizeBytes) &&
                        (source.dateModifiedMillis <= 0L || file.lastModified() / 1000 <= source.dateModifiedMillis / 1000)
                    )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure !is DuplicateTrashInterrupted || item.uriKey !in failure.moved) {
                Log.w("FGallery", "Viewer cleanup move failed", failure)
                viewerTrashErrors.trySend(item.name)
            }
        } finally {
            _uiState.update { it.copy(pendingViewerTrash = it.pendingViewerTrash - item.uriKey) }
        }
    }

    suspend fun moveToRecycleBin(items: List<MediaItem>) =
        moveToRecycleBinWithPreMoveCheck(items) { true }

    /** The recycle bin runs the supplied check again before moving each source. */
    suspend fun moveToRecycleBinWithPreMoveCheck(
        items: List<MediaItem>,
        verifyBeforeMove: suspend (MediaItem) -> Boolean,
    ) {
        moveItemsToRecycleBin(items, observingCleanupViewer, verifyBeforeMove)
    }

    private suspend fun moveItemsToRecycleBin(
        items: List<MediaItem>,
        deferMediaScan: Boolean,
        verifyBeforeMove: suspend (MediaItem) -> Boolean,
    ) {
        moveBinBatch { onMoved ->
            // A stream of cleanup taps only changes the affected app rows.
            // Hand the old paths to Android together when the viewer closes.
            val reconcile: (Set<String>) -> Unit = if (deferMediaScan) {
                { cleanupMediaPaths.addAll(it) }
            } else driveBin::reconcileAndroidIndex
            driveBin.trashWithPreMoveCheck(
                items, onMoved = onMoved, reconcilePaths = reconcile, verifyBeforeMove = verifyBeforeMove,
            )
        }
    }

    private suspend fun checkedComparisonTrashSha256(
        row: CatalogMediaRow,
        item: MediaItem,
    ): String = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val latest = CatalogStore.duplicateRowsBySize(context, row.sizeBytes)
        val jobContext = currentCoroutineContext()
        comparisonTrashSha256(context, row, item, latest) { jobContext.ensureActive() }
    }

    private suspend fun trashVisualDuplicates(rows: List<CatalogMediaRow>): Set<String> = moveBinBatch { onMoved ->
        val rowsByUri = rows.associateBy { it.uri }
        val items = rows.map { row ->
            checkNotNull(row.toDuplicateMediaItem()) { "Исходный файл недоступен: ${row.name}" }
        }
        val preflightDigests = mutableMapOf<String, String>()
        driveBin.trashWithPreMoveCheck(
            items,
            verifyPreflight = { item ->
                preflightDigests[item.uriKey] = checkedComparisonTrashSha256(rowsByUri.getValue(item.uriKey), item)
                true
            },
            onMoved = onMoved,
        ) { item ->
            checkedComparisonTrashSha256(rowsByUri.getValue(item.uriKey), item) == preflightDigests.getValue(item.uriKey)
        }
    }

    internal suspend fun trashConfirmedExactDuplicates(
        reviewed: com.mistermikhail.fgallery.data.ExactDuplicateReview,
    ): Set<String> = moveBinBatch { onMoved ->
        val context = getApplication<Application>()
        driveBin.trashExactDuplicateBatch(
            context,
            reviewed,
            rowsBySizes = { CatalogStore.duplicateRowsBySizes(context, it) },
            movePair = { copy, keeper -> CatalogStore.duplicateMovePair(context, copy, keeper) },
            mediaItem = { row -> checkNotNull(row.toDuplicateMediaItem()).copy(kind = MediaKind.valueOf(row.kind)) },
            onMoved = onMoved,
        )
    }

    private suspend fun moveBinBatch(
        move: suspend ((MediaItem, MediaItem) -> Unit) -> Unit,
    ): Set<String> = withContext(Dispatchers.Main.immediate) {
        val completed = linkedMapOf<String, MediaItem>()
        activeBinMoves.value++
        try {
            move { source, destination ->
                completed[source.uriKey] = destination
                binNotifications.record(source, destination)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw DuplicateTrashInterrupted(completed.keys.toSet(), failure)
        } finally {
            withContext(NonCancellable) {
                try {
                    if (completed.isNotEmpty()) recordBinMoves(completed)
                } finally {
                    activeBinMoves.value--
                    resumeDeferredRefresh()
                }
            }
        }
        completed.keys.toSet()
    }

    private suspend fun recordBinMoves(moved: Map<String, MediaItem>) {
        // Retire any stale scan now. The writer mutex orders its in-flight
        // Room transaction with the delta; the viewer can show the next frame.
        refreshJob?.takeIf { it.isActive }?.let {
            it.cancel()
            if (lastCatalogueScanSucceeded != true) deferredObservedRefresh = true
        }
        pendingBinIndexMoves.putAll(moved)
        withContext(Dispatchers.Default) {
            val current = _uiState.updateAndGet { applyBinMoves(it, moved) }
            LibrarySnapshot.remember(LibrarySnapshot.Value(current.allItems, current.physicalBinUris, current.hasPermission))
        }
        if (binIndexJob?.isActive != true) {
            binIndexJob = viewModelScope.launch {
                while (pendingBinIndexMoves.isNotEmpty() && flushPendingBinIndexMoves()) {
                    currentCoroutineContext().ensureActive()
                }
            }
        }
    }

    private suspend fun flushPendingBinIndexMoves(): Boolean = catalogueWrites.withLock {
        val pending = pendingBinIndexMoves.toMap()
        if (pending.isEmpty()) return@withLock true
        try {
            CatalogStore.recordRecycleBinMoves(getApplication(), pending)
            pending.forEach { (source, destination) -> pendingBinIndexMoves.remove(source, destination) }
            true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("FGallery", "Could not apply recycle-bin changes to Room", failure)
            false
        }
    }

    suspend fun restoreFromRecycleBin(items: List<MediaItem>) {
        try {
            driveBin.restore(items)
            val restored = items.mapTo(hashSetOf()) { it.uriKey }
            // A successful restore changes the URI identity. Order removal
            // after any queued insertion of the same recycle-bin entry.
            catalogueWrites.withLock {
                pendingBinIndexMoves.entries.removeIf { it.value.uriKey in restored }
                runCatching { CatalogStore.removeUris(getApplication(), restored) }
            }
            settingsRepository.removeFromRecycleBin(items.map { it.uriKey })
        } finally { refresh() }
    }

    fun onPermanentlyDeleted(uris: Collection<String>) {
        viewModelScope.launch {
            driveBin.deleted(uris)
            catalogueWrites.withLock {
                pendingBinIndexMoves.entries.removeIf { it.value.uriKey in uris }
                runCatching { CatalogStore.removeUris(getApplication(), uris.toSet()) }
            }
            settingsRepository.removeFromRecycleBin(uris)
            refresh()
        }
    }

    fun openRecycleBin() {
        savedStateHandle.remove<String>("selected_album")
        _uiState.update {
            it.copy(
                recycleBinVisible = true,
                selectedAlbum = null,
                query = "",
                searchVisible = false,
            ).let(::derive)
        }
    }

    fun closeRecycleBin() =
        _uiState.update { it.copy(recycleBinVisible = false) }

    fun openAlbum(name: String) {
        savedStateHandle["selected_album"] = name
        _uiState.update {
            derive(
                it.copy(
                    selectedAlbum = name,
                    query = "",
                    searchVisible = false,
                )
            )
        }
        prewarmAlbum(name)
    }

    fun closeAlbum() {
        savedStateHandle.remove<String>("selected_album")
        _uiState.update {
            derive(
                it.copy(
                    selectedAlbum = null,
                    query = "",
                    searchVisible = false,
                )
            )
        }
    }

    fun setFilter(filter: MediaFilter) =
        _uiState.update { derive(it.copy(filter = filter)) }

    fun setConfirmMove(enabled: Boolean) = settingsRepository.setConfirmMove(enabled)

    fun setConfirmRename(enabled: Boolean) = settingsRepository.setConfirmRename(enabled)

    fun setSortMode(sortMode: SortMode) {
        _uiState.update { derive(it.copy(sortMode = sortMode)) }
        settingsRepository.setSortModeName(sortMode.name)
    }

    fun toggleGridMode() =
        _uiState.update {
            it.copy(
                gridMode = if (it.gridMode == GridMode.MOSAIC) {
                    GridMode.UNIFORM
                } else {
                    GridMode.MOSAIC
                }
            )
        }

    fun closeSearch() =
        _uiState.update { derive(it.copy(searchVisible = false, query = "")) }

    fun toggleSearch() =
        _uiState.update {
            derive(
                it.copy(
                    searchVisible = !it.searchVisible,
                    query = "",
                )
            )
        }

    fun setQuery(query: String) =
        _uiState.update { derive(it.copy(query = query)) }

    fun showSettings() =
        _uiState.update { it.copy(settingsVisible = true) }

    fun hideSettings() =
        _uiState.update { it.copy(settingsVisible = false) }

    fun setQuickExifEnabled(enabled: Boolean) {
        settingsRepository.setQuickExifEnabled(enabled)
    }
}

package com.mistermikhail.fgallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mistermikhail.fgallery.data.LibrarySnapshot
import com.mistermikhail.fgallery.data.DriveLibrary
import com.mistermikhail.fgallery.data.DriveRecycleBin
import com.mistermikhail.fgallery.data.DocumentLibrary
import com.mistermikhail.fgallery.data.AppSettingsRepository
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.MediaRepository
import com.mistermikhail.fgallery.data.ThumbnailCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MediaFilter { ALL, PHOTOS, VIDEOS, RAW }
enum class GridMode { MOSAIC, UNIFORM }
enum class SortMode {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    SIZE_ASC,
}

data class AlbumSummary(
    val name: String,
    val cover: MediaItem,
    val count: Int,
    val newestDateMillis: Long,
    val totalSizeBytes: Long,
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
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val driveBin = DriveRecycleBin(application)
    private var thumbnailJob: kotlinx.coroutines.Job? = null
    private var albumWarmJob: kotlinx.coroutines.Job? = null
    private var refreshJob: kotlinx.coroutines.Job? = null
    private val repository = MediaRepository(application)
    private val settingsRepository = AppSettingsRepository(application)
    private val _uiState = MutableStateFlow(LibrarySnapshot.memory?.let {
        derive(GalleryUiState(allItems = it.items, physicalBinUris = it.bin, hasPermission = it.permission))
    } ?: GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

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

    private fun sortItems(
        items: List<MediaItem>,
        mode: SortMode,
    ): List<MediaItem> =
        when (mode) {
            SortMode.DATE_DESC -> items.sortedByDescending { it.dateTakenMillis }
            SortMode.DATE_ASC -> items.sortedBy { it.dateTakenMillis }
            SortMode.NAME_ASC -> items.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> items.sortedByDescending { it.name.lowercase() }
            SortMode.SIZE_DESC -> items.sortedByDescending { it.sizeBytes }
            SortMode.SIZE_ASC -> items.sortedBy { it.sizeBytes }
        }

    /**
     * Expensive filtering/grouping/sorting happens only when its actual inputs change.
     * Compose reads already-materialized immutable lists during scroll/recomposition.
     */
    private fun derive(state: GalleryUiState): GalleryUiState {
        val available = state.allItems.asSequence()
            .filterNot { it.uriKey in (state.recycleBinUris + state.physicalBinUris) }
            .filter { item ->
                when (state.filter) {
                    MediaFilter.ALL -> true
                    MediaFilter.PHOTOS -> item.kind == MediaKind.IMAGE
                    MediaFilter.VIDEOS -> item.kind == MediaKind.VIDEO
                    MediaFilter.RAW -> item.kind == MediaKind.RAW
                }
            }
            .toList()

        val visible = sortItems(
            available.asSequence()
                .filter {
                    state.selectedAlbum == null ||
                        it.album == state.selectedAlbum
                }
                .filter {
                    state.query.isBlank() ||
                        it.name.contains(state.query, ignoreCase = true) ||
                        it.album.contains(state.query, ignoreCase = true)
                }
                .toList(),
            state.sortMode,
        )

        val recycle = sortItems(
            state.allItems.filter { it.uriKey in (state.recycleBinUris + state.physicalBinUris) },
            state.sortMode,
        )

        val unsortedAlbums = available
            .groupBy { it.album }
            .mapNotNull { (name, items) ->
                if (
                    state.query.isNotBlank() &&
                    !name.contains(state.query, ignoreCase = true)
                ) {
                    return@mapNotNull null
                }

                val sorted = sortItems(items, state.sortMode)
                val cover = sorted.firstOrNull() ?: return@mapNotNull null

                AlbumSummary(
                    name = name,
                    cover = cover,
                    count = items.size,
                    newestDateMillis = items.maxOfOrNull { it.dateTakenMillis } ?: 0L,
                    totalSizeBytes = items.sumOf { it.sizeBytes },
                )
            }

        val albums = when (state.sortMode) {
            SortMode.DATE_DESC -> unsortedAlbums.sortedByDescending { it.newestDateMillis }
            SortMode.DATE_ASC -> unsortedAlbums.sortedBy { it.newestDateMillis }
            SortMode.NAME_ASC -> unsortedAlbums.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> unsortedAlbums.sortedByDescending { it.name.lowercase() }
            SortMode.SIZE_DESC -> unsortedAlbums.sortedByDescending { it.totalSizeBytes }
            SortMode.SIZE_ASC -> unsortedAlbums.sortedBy { it.totalSizeBytes }
        }

        return state.copy(
            visibleItems = visible,
            recycleBinItems = recycle,
            albums = albums,
        )
    }

    fun onPermissionChanged(granted: Boolean) {
        _uiState.update { current ->
            if (granted != current.hasPermission) derive(current.copy(hasPermission = granted, allItems = emptyList()))
            else current.copy(hasPermission = granted)
        }
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.allItems.isEmpty()) {
                LibrarySnapshot.load(getApplication(), _uiState.value.hasPermission)?.let { saved ->
                    _uiState.update { derive(it.copy(allItems = saved.items, physicalBinUris = saved.bin)) }
                }
            }
            _uiState.update { it.copy(isLoading = it.allItems.isEmpty()) }

            val normal = try {
                val media = if (_uiState.value.hasPermission) repository.loadMedia() else emptyList()
                val documents = DocumentLibrary(getApplication()).load()
                (media + documents + DriveLibrary.load(getApplication(), media + documents)).distinctBy { it.uriKey }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { _uiState.value.allItems.filterNot { it.uriKey in _uiState.value.physicalBinUris } }
            val recycled = driveBin.load()
            val items = normal + recycled

            _uiState.update { current ->
                derive(
                    current.copy(
                        allItems = items,
                        physicalBinUris = recycled.mapTo(mutableSetOf()) { it.uriKey },
                        isLoading = false,
                    )
                )
            }

            runCatching { LibrarySnapshot.save(getApplication(), LibrarySnapshot.Value(items, recycled.mapTo(hashSetOf()) { it.uriKey }, _uiState.value.hasPermission)) }
            thumbnailJob?.cancel()
            val albumCovers = items
                .groupBy { it.album }
                .values
                .mapNotNull { albumItems ->
                    albumItems.maxByOrNull { it.dateTakenMillis }
                }

            thumbnailJob = viewModelScope.launch(Dispatchers.IO) {
                // Cache generation must never drive Compose recomposition.
                preloadThumbnailBatches(
                    items = albumCovers,
                    batchSize = 12,
                    highQuality = false,
                    pauseBetweenBatchesMs = 0L,
                )

                val coverIds = albumCovers.mapTo(hashSetOf()) { it.id }
                val remaining = items.filterNot { it.id in coverIds }

                preloadThumbnailBatches(
                    items = remaining,
                    batchSize = 18,
                    highQuality = false,
                    pauseBetweenBatchesMs = 45L,
                )
                preloadThumbnailBatches(
                    items = albumCovers,
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
            it.album == name && it.uriKey !in (snapshot.recycleBinUris + snapshot.physicalBinUris)
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

    suspend fun moveToRecycleBin(items: List<MediaItem>) {
        try { driveBin.trash(items) } finally { refresh() }
    }

    suspend fun restoreFromRecycleBin(items: List<MediaItem>) {
        try {
            driveBin.restore(items)
            settingsRepository.removeFromRecycleBin(items.map { it.uriKey })
        } finally { refresh() }
    }

    fun onPermanentlyDeleted(uris: Collection<String>) {
        viewModelScope.launch {
            driveBin.deleted(uris)
            settingsRepository.removeFromRecycleBin(uris)
            refresh()
        }
    }

    fun openRecycleBin() {
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

    fun closeAlbum() =
        _uiState.update {
            derive(
                it.copy(
                    selectedAlbum = null,
                    query = "",
                    searchVisible = false,
                )
            )
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


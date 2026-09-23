package com.mistermikhail.fgallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mistermikhail.fgallery.data.AppSettingsRepository
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.MediaRepository
import com.mistermikhail.fgallery.data.ThumbnailCache
import kotlinx.coroutines.Dispatchers
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
    val isLoading: Boolean = false,
    val hasPermission: Boolean = false,
    val query: String = "",
    val searchVisible: Boolean = false,
    val filter: MediaFilter = MediaFilter.ALL,
    val gridMode: GridMode = GridMode.MOSAIC,
    val sortMode: SortMode = SortMode.DATE_DESC,
    val selectedAlbum: String? = null,
    val quickExifEnabled: Boolean = true,
    val settingsVisible: Boolean = false,
    val recycleBinVisible: Boolean = false,
    val recycleBinUris: Set<String> = emptySet(),
    val thumbnailCacheVersion: Long = 0L,
) {
    private fun isInRecycleBin(item: MediaItem): Boolean =
        item.uri.toString() in recycleBinUris

    val filteredItems: List<MediaItem>
        get() = allItems.asSequence()
            .filterNot(::isInRecycleBin)
            .filter {
                when (filter) {
                    MediaFilter.ALL -> true
                    MediaFilter.PHOTOS -> it.kind == MediaKind.IMAGE
                    MediaFilter.VIDEOS -> it.kind == MediaKind.VIDEO
                    MediaFilter.RAW -> it.kind == MediaKind.RAW
                }
            }
            .toList()

    private fun sortItems(items: List<MediaItem>): List<MediaItem> =
        when (sortMode) {
            SortMode.DATE_DESC -> items.sortedByDescending { it.dateTakenMillis }
            SortMode.DATE_ASC -> items.sortedBy { it.dateTakenMillis }
            SortMode.NAME_ASC -> items.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> items.sortedByDescending { it.name.lowercase() }
            SortMode.SIZE_DESC -> items.sortedByDescending { it.sizeBytes }
            SortMode.SIZE_ASC -> items.sortedBy { it.sizeBytes }
        }

    val visibleItems: List<MediaItem>
        get() {
            val items = filteredItems.asSequence()
                .filter { selectedAlbum == null || it.album == selectedAlbum }
                .filter {
                    query.isBlank() ||
                        it.name.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
                }
                .toList()

            return sortItems(items)
        }

    val recycleBinItems: List<MediaItem>
        get() = sortItems(
            allItems.filter(::isInRecycleBin)
        )

    val albums: List<AlbumSummary>
        get() {
            val result = filteredItems
                .groupBy { it.album }
                .mapNotNull { (name, items) ->
                    val sorted = sortItems(items)
                    val cover = sorted.firstOrNull() ?: return@mapNotNull null
                    AlbumSummary(
                        name = name,
                        cover = cover,
                        count = items.size,
                        newestDateMillis = items.maxOfOrNull { it.dateTakenMillis } ?: 0L,
                        totalSizeBytes = items.sumOf { it.sizeBytes },
                    )
                }
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

            return when (sortMode) {
                SortMode.DATE_DESC -> result.sortedByDescending { it.newestDateMillis }
                SortMode.DATE_ASC -> result.sortedBy { it.newestDateMillis }
                SortMode.NAME_ASC -> result.sortedBy { it.name.lowercase() }
                SortMode.NAME_DESC -> result.sortedByDescending { it.name.lowercase() }
                SortMode.SIZE_DESC -> result.sortedByDescending { it.totalSizeBytes }
                SortMode.SIZE_ASC -> result.sortedBy { it.totalSizeBytes }
            }
        }
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)
    private val settingsRepository = AppSettingsRepository(application)
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
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
                _uiState.update { it.copy(sortMode = mode) }
            }
        }

        viewModelScope.launch {
            settingsRepository.recycleBinUris.collect { uris ->
                _uiState.update { it.copy(recycleBinUris = uris) }
            }
        }
    }

    fun onPermissionChanged(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) refresh()
    }

    fun refresh() {
        if (!_uiState.value.hasPermission) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val items = runCatching {
                repository.loadMedia()
            }.getOrDefault(emptyList())

            settingsRepository.retainRecycleBin(
                items.mapTo(mutableSetOf()) { it.uri.toString() }
            )

            _uiState.update {
                it.copy(
                    allItems = items,
                    isLoading = false,
                )
            }

            // Prewarm covers first so the start screen stabilizes quickly.
            val albumCovers = items
                .groupBy { it.album }
                .values
                .mapNotNull { albumItems ->
                    albumItems.maxByOrNull { it.dateTakenMillis }
                }

            viewModelScope.launch(Dispatchers.IO) {
                // Fast tier first so the UI fills immediately.
                preloadThumbnailBatches(
                    items = albumCovers,
                    batchSize = 12,
                    highQuality = false,
                )

                val coverIds = albumCovers.mapTo(hashSetOf()) { it.id }
                val remaining = items.filterNot { it.id in coverIds }

                preloadThumbnailBatches(
                    items = remaining,
                    batchSize = 24,
                    highQuality = false,
                )

                // Then progressively replace them with sharp previews.
                preloadThumbnailBatches(
                    items = albumCovers,
                    batchSize = 8,
                    highQuality = true,
                )

                preloadThumbnailBatches(
                    items = remaining,
                    batchSize = 12,
                    highQuality = true,
                )
            }
        }
    }

    private suspend fun preloadThumbnailBatches(
        items: List<MediaItem>,
        batchSize: Int,
        highQuality: Boolean,
    ) {
        items.chunked(batchSize).forEach { batch ->
            val generated = ThumbnailCache.preload(
                context = getApplication<Application>(),
                items = batch,
                highQuality = highQuality,
            )

            if (generated > 0) {
                _uiState.update {
                    it.copy(thumbnailCacheVersion = it.thumbnailCacheVersion + 1L)
                }
            }
        }
    }

    private fun prewarmAlbum(name: String) {
        val items = _uiState.value.allItems
            .filter { it.album == name && it.uri.toString() !in _uiState.value.recycleBinUris }

        viewModelScope.launch(Dispatchers.IO) {
            preloadThumbnailBatches(
                items = items,
                batchSize = 16,
                highQuality = false,
            )
            preloadThumbnailBatches(
                items = items,
                batchSize = 8,
                highQuality = true,
            )
        }
    }

    fun moveToRecycleBin(items: List<MediaItem>) {
        if (items.isEmpty()) return
        settingsRepository.addToRecycleBin(
            items.map { it.uri.toString() }
        )
    }

    fun restoreFromRecycleBin(items: List<MediaItem>) {
        if (items.isEmpty()) return
        settingsRepository.removeFromRecycleBin(
            items.map { it.uri.toString() }
        )
    }

    fun onPermanentlyDeleted(uris: Collection<String>) {
        settingsRepository.removeFromRecycleBin(uris)
        refresh()
    }

    fun openRecycleBin() {
        _uiState.update {
            it.copy(
                recycleBinVisible = true,
                selectedAlbum = null,
                query = "",
                searchVisible = false,
            )
        }
    }

    fun closeRecycleBin() = _uiState.update {
        it.copy(recycleBinVisible = false)
    }

    fun openAlbum(name: String) {
        _uiState.update {
            it.copy(
                selectedAlbum = name,
                query = "",
                searchVisible = false,
            )
        }
        prewarmAlbum(name)
    }

    fun closeAlbum() = _uiState.update {
        it.copy(
            selectedAlbum = null,
            query = "",
            searchVisible = false,
        )
    }

    fun setFilter(filter: MediaFilter) =
        _uiState.update { it.copy(filter = filter) }

    fun setSortMode(sortMode: SortMode) {
        _uiState.update { it.copy(sortMode = sortMode) }
        settingsRepository.setSortModeName(sortMode.name)
    }

    fun toggleGridMode() = _uiState.update {
        it.copy(
            gridMode = if (it.gridMode == GridMode.MOSAIC) {
                GridMode.UNIFORM
            } else {
                GridMode.MOSAIC
            }
        )
    }

    fun toggleSearch() = _uiState.update {
        it.copy(
            searchVisible = !it.searchVisible,
            query = "",
        )
    }

    fun setQuery(query: String) =
        _uiState.update { it.copy(query = query) }

    fun showSettings() =
        _uiState.update { it.copy(settingsVisible = true) }

    fun hideSettings() =
        _uiState.update { it.copy(settingsVisible = false) }

    fun setQuickExifEnabled(enabled: Boolean) {
        settingsRepository.setQuickExifEnabled(enabled)
    }
}

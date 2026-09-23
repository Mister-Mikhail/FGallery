package com.mistermikhail.fgallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mistermikhail.fgallery.data.AppSettingsRepository
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MediaFilter { ALL, PHOTOS, VIDEOS, RAW }
enum class GridMode { MOSAIC, UNIFORM }
enum class SortMode { DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC, SIZE_DESC }

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
    val recycleBinItems: List<MediaItem> = emptyList(),
    val recycleBinLoading: Boolean = false,
) {
    val filteredItems: List<MediaItem>
        get() = allItems.asSequence()
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
                val mode = runCatching { SortMode.valueOf(saved) }.getOrDefault(SortMode.DATE_DESC)
                _uiState.update { it.copy(sortMode = mode) }
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
            val items = runCatching { repository.loadMedia() }.getOrDefault(emptyList())
            _uiState.update { it.copy(allItems = items, isLoading = false) }
        }
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
        refreshRecycleBin()
    }

    fun closeRecycleBin() = _uiState.update {
        it.copy(recycleBinVisible = false, recycleBinItems = emptyList())
    }

    fun refreshRecycleBin() {
        if (!_uiState.value.hasPermission) return
        viewModelScope.launch {
            _uiState.update { it.copy(recycleBinLoading = true) }
            val items = runCatching { repository.loadTrash() }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    recycleBinItems = items,
                    recycleBinLoading = false,
                )
            }
        }
    }

    fun openAlbum(name: String) = _uiState.update {
        it.copy(selectedAlbum = name, query = "", searchVisible = false)
    }

    fun closeAlbum() = _uiState.update {
        it.copy(selectedAlbum = null, query = "", searchVisible = false)
    }

    fun setFilter(filter: MediaFilter) = _uiState.update { it.copy(filter = filter) }

    fun setSortMode(sortMode: SortMode) {
        _uiState.update { it.copy(sortMode = sortMode) }
        settingsRepository.setSortModeName(sortMode.name)
    }

    fun toggleGridMode() = _uiState.update {
        it.copy(gridMode = if (it.gridMode == GridMode.MOSAIC) GridMode.UNIFORM else GridMode.MOSAIC)
    }

    fun toggleSearch() = _uiState.update { it.copy(searchVisible = !it.searchVisible, query = "") }
    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun showSettings() = _uiState.update { it.copy(settingsVisible = true) }
    fun hideSettings() = _uiState.update { it.copy(settingsVisible = false) }

    fun setQuickExifEnabled(enabled: Boolean) {
        settingsRepository.setQuickExifEnabled(enabled)
    }
}

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

            return when (sortMode) {
                SortMode.DATE_DESC -> items.sortedByDescending { it.dateTakenMillis }
                SortMode.DATE_ASC -> items.sortedBy { it.dateTakenMillis }
                SortMode.NAME_ASC -> items.sortedBy { it.name.lowercase() }
                SortMode.NAME_DESC -> items.sortedByDescending { it.name.lowercase() }
                SortMode.SIZE_DESC -> items.sortedByDescending { it.sizeBytes }
            }
        }

    val albums: List<AlbumSummary>
        get() = filteredItems
            .groupBy { it.album }
            .mapNotNull { (name, items) ->
                items.firstOrNull()?.let { AlbumSummary(name, it, items.size) }
            }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
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

    fun openAlbum(name: String) = _uiState.update {
        it.copy(selectedAlbum = name, query = "", searchVisible = false)
    }

    fun closeAlbum() = _uiState.update {
        it.copy(selectedAlbum = null, query = "", searchVisible = false)
    }

    fun setFilter(filter: MediaFilter) = _uiState.update { it.copy(filter = filter) }
    fun setSortMode(sortMode: SortMode) = _uiState.update { it.copy(sortMode = sortMode) }

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

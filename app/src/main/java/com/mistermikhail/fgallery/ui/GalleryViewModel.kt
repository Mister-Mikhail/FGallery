package com.mistermikhail.fgallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

data class GalleryUiState(
    val allItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasPermission: Boolean = false,
    val query: String = "",
    val searchVisible: Boolean = false,
    val filter: MediaFilter = MediaFilter.ALL,
    val gridMode: GridMode = GridMode.MOSAIC,
) {
    val visibleItems: List<MediaItem>
        get() = allItems.asSequence()
            .filter {
                when (filter) {
                    MediaFilter.ALL -> true
                    MediaFilter.PHOTOS -> it.kind == MediaKind.IMAGE
                    MediaFilter.VIDEOS -> it.kind == MediaKind.VIDEO
                    MediaFilter.RAW -> it.kind == MediaKind.RAW
                }
            }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
            }
            .toList()
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

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

    fun setFilter(filter: MediaFilter) = _uiState.update { it.copy(filter = filter) }

    fun toggleGridMode() = _uiState.update {
        it.copy(gridMode = if (it.gridMode == GridMode.MOSAIC) GridMode.UNIFORM else GridMode.MOSAIC)
    }

    fun toggleSearch() = _uiState.update { it.copy(searchVisible = !it.searchVisible, query = "") }
    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }
}

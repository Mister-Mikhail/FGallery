package com.mistermikhail.fgallery.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettingsRepository(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _quickExifEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_QUICK_EXIF_ENABLED, true)
    )
    val quickExifEnabled: StateFlow<Boolean> = _quickExifEnabled.asStateFlow()

    private val _sortModeName = MutableStateFlow(
        preferences.getString(KEY_SORT_MODE, DEFAULT_SORT_MODE) ?: DEFAULT_SORT_MODE
    )
    val sortModeName: StateFlow<String> = _sortModeName.asStateFlow()

    private val _recycleBinUris = MutableStateFlow(
        preferences.getStringSet(KEY_RECYCLE_BIN_URIS, emptySet())
            ?.toSet()
            .orEmpty()
    )
    val recycleBinUris: StateFlow<Set<String>> = _recycleBinUris.asStateFlow()

    fun setQuickExifEnabled(enabled: Boolean) {
        preferences
            .edit()
            .putBoolean(KEY_QUICK_EXIF_ENABLED, enabled)
            .apply()

        _quickExifEnabled.value = enabled
    }

    fun setSortModeName(value: String) {
        preferences
            .edit()
            .putString(KEY_SORT_MODE, value)
            .apply()

        _sortModeName.value = value
    }

    fun addToRecycleBin(uris: Collection<String>) {
        updateRecycleBin(_recycleBinUris.value + uris)
    }

    fun removeFromRecycleBin(uris: Collection<String>) {
        updateRecycleBin(_recycleBinUris.value - uris.toSet())
    }

    fun retainRecycleBin(existingUris: Set<String>) {
        val pruned = _recycleBinUris.value.intersect(existingUris)
        if (pruned != _recycleBinUris.value) {
            updateRecycleBin(pruned)
        }
    }

    private fun updateRecycleBin(value: Set<String>) {
        val copy = value.toSet()
        preferences
            .edit()
            .putStringSet(KEY_RECYCLE_BIN_URIS, copy)
            .apply()
        _recycleBinUris.value = copy
    }

    companion object {
        private const val PREFERENCES_NAME = "fgallery_settings"
        private const val KEY_QUICK_EXIF_ENABLED = "quick_exif_enabled"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_RECYCLE_BIN_URIS = "recycle_bin_uris"
        private const val DEFAULT_SORT_MODE = "DATE_DESC"
    }
}

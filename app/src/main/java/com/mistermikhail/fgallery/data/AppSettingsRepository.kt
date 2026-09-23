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

    fun setQuickExifEnabled(enabled: Boolean) {
        preferences
            .edit()
            .putBoolean(KEY_QUICK_EXIF_ENABLED, enabled)
            .apply()

        _quickExifEnabled.value = enabled
    }

    companion object {
        private const val PREFERENCES_NAME = "fgallery_settings"
        private const val KEY_QUICK_EXIF_ENABLED = "quick_exif_enabled"
    }
}

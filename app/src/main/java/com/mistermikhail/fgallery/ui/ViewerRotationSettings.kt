package com.mistermikhail.fgallery.ui

import android.content.Context
import android.content.SharedPreferences
import com.mistermikhail.fgallery.data.MediaItem

internal const val ROTATION_THRESHOLD_KEY = "viewer_rotation_threshold_degrees"
internal const val REMEMBER_ROTATION_KEY = "viewer_remember_rotation"

internal fun viewerGesturePreferences(context: Context): SharedPreferences =
    context.getSharedPreferences("fgallery_settings", Context.MODE_PRIVATE)

internal fun SharedPreferences.rotationThreshold(): Int =
    getInt(ROTATION_THRESHOLD_KEY, QuarterTurnGesture.DEFAULT_THRESHOLD).coerceIn(0, QuarterTurnGesture.MAX_THRESHOLD)

internal fun SharedPreferences.rememberImageRotation(): Boolean = getBoolean(REMEMBER_ROTATION_KEY, false)

internal fun imageOrientationPreferences(context: Context): SharedPreferences =
    context.getSharedPreferences("fgallery_viewer_orientation", Context.MODE_PRIVATE)

internal fun SharedPreferences.imageQuarterTurns(item: MediaItem): Int =
    getString(orientationKey(item), null)?.takeIf { it.startsWith(orientationVersion(item)) }
        ?.substringAfterLast(':')?.toIntOrNull()?.takeIf { it in 1..3 } ?: 0

internal fun SharedPreferences.saveImageQuarterTurns(item: MediaItem, turns: Int) {
    val editor = edit()
    val key = orientationKey(item)
    if (turns == 0) editor.remove(key)
    else editor.putString(key, orientationVersion(item) + turns)
    editor.apply()
}

private fun orientationVersion(item: MediaItem): String = "${item.dateModifiedMillis}:${item.sizeBytes}:"

private fun orientationKey(item: MediaItem): String =
    item.sourcePath.ifBlank { if (item.uri.scheme == "file") item.uri.path ?: item.uriKey else item.uriKey }

package com.mistermikhail.fgallery.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat

sealed interface CaptureLocation {
    data class Available(val coordinates: ExifGpsCoordinates) : CaptureLocation
    data object PermissionRequired : CaptureLocation
    data object Missing : CaptureLocation
    data object Unavailable : CaptureLocation
}

internal fun Context.canReadOriginalLocation(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

internal fun Uri.isMediaStoreItem(): Boolean =
    scheme == "content" && authority == MediaStore.AUTHORITY &&
        pathSegments.size >= 3 && lastPathSegment?.toLongOrNull() != null

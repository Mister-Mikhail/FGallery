package com.mistermikhail.fgallery.data

import java.util.Locale

/**
 * Validated coordinates extracted from the source media's metadata.
 * Never substitute device location or a preview's coordinates.
 */
data class ExifGpsCoordinates private constructor(
    val latitude: Double,
    val longitude: Double,
) {
    fun display(): String = String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude)

    companion object {
        fun fromExif(latLong: DoubleArray?): ExifGpsCoordinates? {
            if (latLong == null || latLong.size != 2) return null
            val lat = latLong[0]
            val lon = latLong[1]
            if (!lat.isFinite() || !lon.isFinite()) return null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            return ExifGpsCoordinates(lat, lon)
        }
    }
}

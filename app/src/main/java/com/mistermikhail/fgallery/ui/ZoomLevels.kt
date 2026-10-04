package com.mistermikhail.fgallery.ui

/** Telephoto uses source-pixel scale, so the first stop must depend on fit scale. */
internal fun firstDoubleTapZoom(fitScale: Float, maximum: Float): Float {
    val minimum = fitScale.coerceAtLeast(0.0001f)
    val limit = maximum.coerceAtLeast(minimum)
    return minimum + (limit - minimum) * 0.30f
}

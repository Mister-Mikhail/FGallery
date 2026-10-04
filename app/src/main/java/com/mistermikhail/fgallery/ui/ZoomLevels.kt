package com.mistermikhail.fgallery.ui

/** One policy for every still/document format; the current scale is the source of truth. */
internal fun nextDoubleTapZoom(current: Float, fit: Float, maximum: Float): Float {
    val base = fit.takeIf { it.isFinite() && it > 0f } ?: 1f
    val max = maximum.takeIf { it.isFinite() && it >= base } ?: base * 8f
    val first = (base * 1.30f).coerceAtMost(max)
    val second = (max * .90f).coerceAtLeast(first)
    val relative = current / base
    return when {
        !relative.isFinite() || relative <= 1.02f -> first
        current < second * .98f -> second
        else -> base
    }
}

internal fun firstDoubleTapZoom(fitScale: Float, maximum: Float): Float =
    nextDoubleTapZoom(fitScale, fitScale, maximum.coerceAtLeast(fitScale))

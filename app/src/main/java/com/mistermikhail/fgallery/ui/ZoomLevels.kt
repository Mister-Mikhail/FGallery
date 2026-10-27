package com.mistermikhail.fgallery.ui

import kotlin.math.pow

/** Stage 1 TZ v1: fraction of the path in doublings, not of the linear scale range. */
internal fun secondDoubleTapZoom(fit: Float, maximum: Float, power: Float = .85f): Float {
    val base = fit.takeIf { it.isFinite() && it > 0f } ?: 1f
    val max = maximum.takeIf { it.isFinite() && it >= base } ?: base * 8f
    val p = power.takeIf { it.isFinite() }?.coerceIn(0.80f, 0.90f) ?: .85f
    return (base * (max / base).pow(p)).coerceIn(base, max)
}

/** Current scale decides the next step; pinch-to-fit resets the cycle. */
internal fun nextDoubleTapZoom(
    current: Float,
    fit: Float,
    maximum: Float,
    smallImage: Boolean = false,
    power: Float = .85f,
): Float {
    val base = fit.takeIf { it.isFinite() && it > 0f } ?: 1f
    val max = maximum.takeIf { it.isFinite() && it >= base } ?: base * 8f
    val first = (base * 2f).coerceAtMost(max)
    val second = secondDoubleTapZoom(base, max, power)
    val relative = current / base
    return when {
        !relative.isFinite() || relative <= 1.02f -> first
        smallImage || second <= first * 1.02f -> base
        current < second * .98f -> second
        else -> base
    }
}

internal fun firstDoubleTapZoom(fitScale: Float, maximum: Float): Float =
    nextDoubleTapZoom(fitScale, fitScale, maximum.coerceAtLeast(fitScale))


/**
 * S23-revised raster targets, requested after testing the 0.3.0 diagnostics:
 * 50% and 160% of a source pixel, not percentages relative to fitted size.
 * Preserve the small-image fit -> 2x fit -> fit cycle from approved TZ v1.
 * PDF/SVG deliberately continue to use nextDoubleTapZoom() and the agreed p.
 */
internal fun nextRasterDoubleTapZoom(
    current: Float,
    fit: Float,
    maximum: Float,
    smallImage: Boolean = false,
): Float {
    val base = fit.takeIf { it.isFinite() && it > 0f } ?: 1f
    val ceiling = maximum.takeIf { it.isFinite() && it >= base } ?: maxOf(8f, 8f * base)
    if (smallImage) return nextDoubleTapZoom(current, base, ceiling, smallImage = true)
    val first = 0.50f.coerceIn(base, ceiling)
    val second = 1.60f.coerceIn(base, ceiling)
    val relative = current / base
    return when {
        !relative.isFinite() || relative <= 1.02f -> first
        second <= first * 1.02f -> base
        current < second * .98f -> second
        else -> base
    }
}

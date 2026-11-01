package com.mistermikhail.fgallery.ui

import kotlin.math.abs

/** Stage 1 sensitivity: moving through the 60%-high allowed zone changes half the range. */
internal fun videoAdjustmentFraction(start: Float, displacementY: Float, viewportHeight: Float): Float {
    if (viewportHeight <= 0f || !viewportHeight.isFinite()) return start.coerceIn(0f, 1f)
    return (start - displacementY / (viewportHeight * .60f) * .50f).coerceIn(0f, 1f)
}

/** Wait for a clear vertical gesture; let the gallery pager keep horizontal swipes. */
internal fun isUnambiguousVertical(dx: Float, dy: Float, touchSlop: Float): Boolean =
    abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.5f

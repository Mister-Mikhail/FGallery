package com.mistermikhail.fgallery.ui

import kotlin.math.abs

/** S23 feedback: one full swipe through the safe 60%-high zone spans the entire range. */
internal fun videoAdjustmentFraction(start: Float, displacementY: Float, viewportHeight: Float): Float {
    if (viewportHeight <= 0f || !viewportHeight.isFinite()) return start.coerceIn(0f, 1f)
    return (start - displacementY / (viewportHeight * .60f)).coerceIn(0f, 1f)
}

/** Wait for a clear vertical gesture; let the gallery pager keep horizontal swipes. */
internal fun isUnambiguousVertical(dx: Float, dy: Float, touchSlop: Float): Boolean =
    abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.5f

package com.mistermikhail.fgallery.ui

internal data class CropBounds(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    fun drag(corner: Int, dx: Float, dy: Float): CropBounds {
        if (!dx.isFinite() || !dy.isFinite()) return this
        val minimum = .05f
        return when (corner) {
            0 -> copy(left = (left + dx).coerceIn(0f, right - minimum), top = (top + dy).coerceIn(0f, bottom - minimum))
            1 -> copy(right = (right + dx).coerceIn(left + minimum, 1f), top = (top + dy).coerceIn(0f, bottom - minimum))
            2 -> copy(right = (right + dx).coerceIn(left + minimum, 1f), bottom = (bottom + dy).coerceIn(top + minimum, 1f))
            3 -> copy(left = (left + dx).coerceIn(0f, right - minimum), bottom = (bottom + dy).coerceIn(top + minimum, 1f))
            else -> this
        }
    }
}

internal data class TrimWindow(val start: Float = 0f, val end: Float = 1f) {
    fun drag(startHandle: Boolean, position: Float, durationMillis: Long): TrimWindow {
        if (!position.isFinite() || durationMillis <= 0) return this
        val minimum = (100f / durationMillis).coerceIn(.001f, 1f)
        return if (startHandle) copy(start = position.coerceIn(0f, end - minimum))
        else copy(end = position.coerceIn(start + minimum, 1f))
    }
}

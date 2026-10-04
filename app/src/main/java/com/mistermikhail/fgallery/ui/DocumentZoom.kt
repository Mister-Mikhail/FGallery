package com.mistermikhail.fgallery.ui

internal data class DocumentTransform(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f)

/** Coordinates are measured in the untransformed container, with a central transform origin. */
internal fun transformDocument(
    old: DocumentTransform, focusX: Float, focusY: Float,
    panX: Float, panY: Float, targetScale: Float, width: Float, height: Float,
): DocumentTransform {
    val zoom = targetScale.coerceIn(1f, 8f)
    val ratio = zoom / old.scale
    val x = old.x * ratio + (focusX - width / 2f) * (1f - ratio) + panX
    val y = old.y * ratio + (focusY - height / 2f) * (1f - ratio) + panY
    val maxX = width * (zoom - 1f) / 2f
    val maxY = height * (zoom - 1f) / 2f
    return DocumentTransform(zoom, x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
}

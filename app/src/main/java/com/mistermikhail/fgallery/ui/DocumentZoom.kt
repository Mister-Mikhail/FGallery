package com.mistermikhail.fgallery.ui

internal data class DocumentTransform(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f)

/** Coordinates are measured in the untransformed container, with a central transform origin. */
internal fun transformDocument(
    old: DocumentTransform, focusX: Float, focusY: Float,
    panX: Float, panY: Float, targetScale: Float, width: Float, height: Float,
): DocumentTransform {
    val safeOld = old.takeIf { it.scale.isFinite() && it.scale >= 1f && it.x.isFinite() && it.y.isFinite() } ?: DocumentTransform()
    if (!focusX.isFinite() || !focusY.isFinite() || !panX.isFinite() || !panY.isFinite() ||
        !targetScale.isFinite() || !width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return safeOld
    val zoom = targetScale.coerceIn(1f, 8f)
    if (zoom == 1f) return DocumentTransform()
    val ratio = zoom / safeOld.scale
    val x = safeOld.x * ratio + (focusX - width / 2f) * (1f - ratio) + panX
    val y = safeOld.y * ratio + (focusY - height / 2f) * (1f - ratio) + panY
    val maxX = width * (zoom - 1f) / 2f
    val maxY = height * (zoom - 1f) / 2f
    return DocumentTransform(zoom, x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
}

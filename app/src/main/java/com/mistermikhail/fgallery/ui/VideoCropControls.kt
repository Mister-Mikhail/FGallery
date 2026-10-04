package com.mistermikhail.fgallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
internal fun CropFrame(bounds: CropBounds, enabled: Boolean, onChange: (CropBounds) -> Unit) {
    val latest by rememberUpdatedState(bounds)
    val latestChange by rememberUpdatedState(onChange)
    Canvas(Modifier.fillMaxSize().testTag("video-crop-area")
        .semantics { stateDescription = "Crop ${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}" }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            var corner = -1
            detectDragGestures(onDragStart = { point ->
                val current = latest
                val points = listOf(
                    Offset(size.width * current.left, size.height * current.top),
                    Offset(size.width * current.right, size.height * current.top),
                    Offset(size.width * current.right, size.height * current.bottom),
                    Offset(size.width * current.left, size.height * current.bottom),
                )
                corner = points.indices.minByOrNull { (points[it] - point).getDistance() } ?: -1
                if (corner >= 0 && (points[corner] - point).getDistance() > 48.dp.toPx()) corner = -1
            }, onDragEnd = { corner = -1 }, onDragCancel = { corner = -1 }) { change, delta ->
                if (corner >= 0 && size.width > 0 && size.height > 0) {
                    change.consume()
                    latestChange(latest.drag(corner, delta.x / size.width, delta.y / size.height))
                }
            }
        }) {
        val l = size.width * bounds.left; val r = size.width * bounds.right
        val t = size.height * bounds.top; val b = size.height * bounds.bottom
        val shade = Color.Black.copy(alpha = .58f)
        drawRect(shade, Offset.Zero, Size(size.width, t))
        drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
        drawRect(shade, Offset(0f, t), Size(l, b - t))
        drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
        for (step in 1..2) {
            val x = l + (r - l) * step / 3f; val y = t + (b - t) * step / 3f
            drawLine(Color.White.copy(alpha = .4f), Offset(x, t), Offset(x, b), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = .4f), Offset(l, y), Offset(r, y), 1.dp.toPx())
        }
        for (point in listOf(Offset(l, t), Offset(r, t), Offset(r, b), Offset(l, b))) {
            drawCircle(Color.Black.copy(alpha = .8f), 9.dp.toPx(), point)
            drawCircle(Color.White, 6.dp.toPx(), point)
        }
    }
}

@Composable
internal fun TrimTimeline(window: TrimWindow, durationMillis: Long, frames: List<Bitmap>, shownMillis: Long,
                          enabled: Boolean, onChange: (TrimWindow, Boolean) -> Unit) {
    val latest by rememberUpdatedState(window)
    val latestChange by rememberUpdatedState(onChange)
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(76.dp).testTag("video-trim-timeline")
        .semantics { stateDescription = "Trim ${window.start},${window.end}; Frame $shownMillis" }
        .pointerInput(enabled, durationMillis) {
            if (!enabled || durationMillis <= 0) return@pointerInput
            var startHandle = true
            fun move(point: Offset) {
                val margin = 12.dp.toPx()
                val position = ((point.x - margin) / (size.width - margin * 2)).coerceIn(0f, 1f)
                latestChange(latest.drag(startHandle, position, durationMillis), startHandle)
            }
            detectDragGestures(onDragStart = { point ->
                val margin = 12.dp.toPx(); val width = size.width - margin * 2
                startHandle = abs(point.x - (margin + width * latest.start)) <= abs(point.x - (margin + width * latest.end))
                move(point)
            }) { change, _ -> change.consume(); move(change.position) }
        }) {
        val margin = 12.dp.toPx(); val trackWidth = size.width - margin * 2
        val top = 8.dp.toPx(); val height = size.height - top * 2
        drawRect(Color.DarkGray, Offset(margin, top), Size(trackWidth, height))
        if (frames.isNotEmpty()) {
            val cellWidth = trackWidth / frames.size
            frames.forEachIndexed { index, bitmap ->
                drawImage(bitmap.asImageBitmap(), dstOffset = IntOffset((margin + index * cellWidth).toInt(), top.toInt()),
                    dstSize = IntSize(cellWidth.toInt() + 1, height.toInt()))
            }
        }
        val start = margin + trackWidth * window.start; val end = margin + trackWidth * window.end
        drawRect(Color.Black.copy(alpha = .65f), Offset(margin, top), Size(start - margin, height))
        drawRect(Color.Black.copy(alpha = .65f), Offset(end, top), Size(margin + trackWidth - end, height))
        drawRect(accent, Offset(start, top), Size(end - start, height), style = Stroke(2.dp.toPx()))
        for (x in listOf(start, end)) {
            drawRoundRect(accent, Offset(x - 8.dp.toPx(), 0f), Size(16.dp.toPx(), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            drawLine(Color.White, Offset(x, size.height * .35f), Offset(x, size.height * .65f), 2.dp.toPx())
        }
        if (durationMillis > 0) {
            val x = margin + trackWidth * (shownMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
            drawLine(Color.White, Offset(x, top), Offset(x, top + height), 2.dp.toPx())
        }
    }
}

internal fun videoTime(millis: Long): String = "%02d:%02d.%03d".format(millis / 60000, millis / 1000 % 60, millis % 1000)

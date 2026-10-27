package com.mistermikhail.fgallery.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compact non-blocking RAW loading animation.
 * Letters travel around the same circular track with staggered phases,
 * slightly accelerating/closing their spacing like a chasing train.
 */
@Composable
internal fun RawOpeningIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "raw-open-motion")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "raw-open-orbit",
    )
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
    }
    Box(
        modifier = modifier.size(124.dp)
            .background(Color.Black.copy(alpha = 0.72f), CircleShape)
            .semantics { contentDescription = "Открываем RAW" }
            .testTag("raw-open-indicator"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(112.dp)) {
            val radius = size.minDimension * .355f
            val centreX = size.width / 2f
            val centreY = size.height / 2f
            paint.textSize = 14.sp.toPx()
            drawCircle(
                color = Color.White.copy(alpha = .14f),
                radius = radius,
                style = Stroke(width = 1.dp.toPx()),
            )
            val caption = "Open Raw"
            caption.forEachIndexed { i, glyph ->
                if (glyph != ' ') {
                    // Distinct phase offsets keep the letters chasing one another.
                    val chase = 4.5f * sin(Math.toRadians((progress * 2f - i * 37f).toDouble())).toFloat()
                    val angle = progress - 92f - i * 19f + chase
                    val radians = Math.toRadians(angle.toDouble())
                    val x = centreX + radius * cos(radians).toFloat()
                    val y = centreY + radius * sin(radians).toFloat()
                    paint.color = if (i >= 5) android.graphics.Color.rgb(255, 117, 74)
                        else android.graphics.Color.WHITE
                    val canvas = drawContext.canvas.nativeCanvas
                    canvas.save()
                    canvas.translate(x, y)
                    canvas.rotate(angle + 90f)
                    canvas.drawText(glyph.toString(), 0f, paint.textSize * .34f, paint)
                    canvas.restore()
                }
            }
        }
    }
}

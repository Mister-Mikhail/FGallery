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

internal const val RAW_INDICATOR_SIZE_DP = 56

/**
 * Advancing clockwise around the top arc keeps "Open Raw" in its natural
 * order. A bounded phase wave makes characters catch up to their neighbours,
 * but never reverses the sequence or causes them to overtake one another.
 */
internal fun rawLetterOrbitAngle(rotation: Float, index: Int): Float {
    val chase = 3.2f * sin(Math.toRadians((rotation * 2f - index * 38f).toDouble())).toFloat()
    return rotation - 160f + index * 20f + chase
}

/** Minimal non-blocking RAW loading indicator, centred by the owning viewer. */
@Composable
internal fun RawOpeningIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "raw-open-motion")
    val rotation by transition.animateFloat(
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
        modifier = modifier
            .size(RAW_INDICATOR_SIZE_DP.dp)
            // A subtle backdrop retains contrast but does not obscure the photo.
            .background(Color.Black.copy(alpha = .24f), CircleShape)
            .semantics { contentDescription = "Открываем RAW" }
            .testTag("raw-open-indicator"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(52.dp)) {
            val radius = size.minDimension * .40f
            val centreX = size.width / 2f
            val centreY = size.height / 2f
            paint.textSize = 9.sp.toPx()
            drawCircle(
                color = Color.White.copy(alpha = .16f),
                radius = radius,
                style = Stroke(width = 0.75f.dp.toPx()),
            )
            val caption = "Open Raw"
            val canvas = drawContext.canvas.nativeCanvas
            caption.forEachIndexed { index, glyph ->
                if (glyph != ' ') {
                    val radians = Math.toRadians(rawLetterOrbitAngle(rotation, index).toDouble())
                    val x = centreX + radius * cos(radians).toFloat()
                    val y = centreY + radius * sin(radians).toFloat()
                    paint.color = if (index >= 5) android.graphics.Color.rgb(255, 117, 74)
                        else android.graphics.Color.WHITE
                    // Keep characters upright. Rotating each glyph by the orbit tangent
                    // made the old wordmark appear mirrored/upside down on the S23.
                    canvas.drawText(glyph.toString(), x, y + paint.textSize * .34f, paint)
                }
            }
        }
    }
}

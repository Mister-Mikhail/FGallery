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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

internal const val RAW_INDICATOR_HEIGHT_DP = 34
internal const val RAW_INDICATOR_TRACK_MULTIPLIER = 1.5f
internal const val RAW_INDICATOR_TEXT = "OPEN RAW"

/**
 * Each upright letter follows the preceding letter with a small phase delay.
 * One sine cycle moves the caption left -> right -> left continuously.
 * The available horizontal track is exactly 150% of the measured caption width.
 */
internal fun rawLetterChaseOffset(phaseRadians: Float, index: Int, textWidth: Float): Float {
    if (textWidth <= 0f) return 0f
    val laggedPhase = phaseRadians - index * .13f
    val normalized = (1f + sin(laggedPhase)) * .5f
    return normalized * (RAW_INDICATOR_TRACK_MULTIPLIER - 1f) * textWidth
}

/** Subtle horizontal RAW decoder status, centered by the image viewer. */
@Composable
internal fun RawOpeningIndicator(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val fontPx = with(density) { 15.sp.toPx() }
    val paint = remember(fontPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = fontPx
            textAlign = Paint.Align.LEFT
            setShadowLayer(2f, 0f, 1f, android.graphics.Color.BLACK)
        }
    }
    val captionWidth = remember(fontPx) { paint.measureText(RAW_INDICATOR_TEXT) }
    val trackWidth = with(density) { (captionWidth * RAW_INDICATOR_TRACK_MULTIPLIER).toDp() }
    val transition = rememberInfiniteTransition(label = "raw-open-chase")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "raw-open-horizontal-chase",
    )

    Box(
        modifier = modifier.width(trackWidth).height(RAW_INDICATOR_HEIGHT_DP.dp)
            .background(Color.Black.copy(alpha = .22f), RoundedCornerShape(8.dp))
            .semantics { contentDescription = "Открываем RAW" }
            .testTag("raw-open-indicator"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.width(trackWidth).height(RAW_INDICATOR_HEIGHT_DP.dp)) {
            val canvas = drawContext.canvas.nativeCanvas
            val baseline = (size.height - paint.ascent() - paint.descent()) * .5f
            var advance = 0f
            RAW_INDICATOR_TEXT.forEachIndexed { index, glyph ->
                val glyphText = glyph.toString()
                if (glyph != ' ') {
                    paint.color = if (index >= 5) android.graphics.Color.rgb(255, 117, 74)
                        else android.graphics.Color.WHITE
                    canvas.drawText(
                        glyphText,
                        advance + rawLetterChaseOffset(phase, index, captionWidth),
                        baseline,
                        paint,
                    )
                }
                advance += paint.measureText(glyphText)
            }
        }
    }
}

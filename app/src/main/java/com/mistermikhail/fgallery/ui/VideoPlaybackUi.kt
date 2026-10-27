package com.mistermikhail.fgallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.roundToLong

internal enum class VideoAdjustmentSide { BRIGHTNESS, VOLUME }

/** Keep a 20–80% height start zone; reserve ~28dp from system Back edges. */
internal fun videoGestureSide(
    x: Float, y: Float, width: Float, height: Float, excludedEdgePx: Float,
): VideoAdjustmentSide? {
    if (width <= excludedEdgePx * 2 || height <= 0f ||
        x <= excludedEdgePx || x >= width - excludedEdgePx ||
        y <= height * .20f || y >= height * .80f) return null
    return if (x < width / 2f) VideoAdjustmentSide.BRIGHTNESS else VideoAdjustmentSide.VOLUME
}

/** One complete animation for both video bars and all inline metadata. */
@Composable
internal fun UnifiedVideoChrome(
    player: Player,
    filename: String,
    visible: Boolean,
    topBar: @Composable () -> Unit,
) {
    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf(0L) }
    var dragging by remember(player) { mutableStateOf(false) }
    var sliderFraction by remember(player) { mutableFloatStateOf(0f) }

    LaunchedEffect(player) {
        while (isActive) {
            durationMs = player.duration.coerceAtLeast(0L)
            positionMs = player.currentPosition.coerceAtLeast(0L)
            if (!dragging && durationMs > 0L)
                sliderFraction = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            delay(140)
        }
    }

    AnimatedVisibility(
        visible = visible, enter = fadeIn(), exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()) { topBar() }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .86f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = filename, color = Color.White.copy(alpha = .78f),
                        maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.weight(1f).padding(start = 6.dp),
                    )
                    IconButton(onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                    }) {
                        Icon(
                            if (player.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (player.isPlaying) "Пауза" else "Воспроизвести",
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = "${formatVideoTimestamp(positionMs)} / ${formatVideoTimestamp(durationMs)}",
                        color = Color.White,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Slider(
                    value = sliderFraction.coerceIn(0f, 1f),
                    onValueChange = { sliderFraction = it; dragging = true },
                    onValueChangeFinished = {
                        if (durationMs > 0L) player.seekTo((durationMs * sliderFraction).roundToLong())
                        dragging = false
                    },
                    modifier = Modifier.fillMaxWidth().height(26.dp).testTag("video-seek"),
                )
            }
        }
    }
}

internal fun formatVideoTimestamp(milliseconds: Long): String {
    val secs = milliseconds.coerceAtLeast(0L) / 1000
    return if (secs >= 3600L)
        String.format(Locale.ROOT, "%d:%02d:%02d", secs / 3600, (secs / 60) % 60, secs % 60)
    else String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60)
}

@Composable
internal fun VideoAdjustmentIndicator(side: VideoAdjustmentSide, value: Float) {
    val fraction = value.coerceIn(0f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.background(Color.Black.copy(alpha = .65f), RoundedCornerShape(9.dp))
            .padding(9.dp),
    ) {
        Icon(
            imageVector = if (side == VideoAdjustmentSide.VOLUME) Icons.Outlined.VolumeUp
                else Icons.Outlined.BrightnessHigh,
            contentDescription = if (side == VideoAdjustmentSide.VOLUME) "Громкость" else "Яркость",
            tint = Color.White,
        )
        Box(
            modifier = Modifier.padding(top = 6.dp).height(115.dp).width(7.dp)
                .background(Color.White.copy(alpha = .25f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(fraction)
                .clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF5A36)))
        }
    }
}

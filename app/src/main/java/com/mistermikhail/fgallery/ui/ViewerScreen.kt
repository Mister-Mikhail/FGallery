package com.mistermikhail.fgallery.ui

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    initialItem: MediaItem,
    onBack: () -> Unit,
    onTrash: (MediaItem) -> Unit,
    quickExifEnabled: Boolean,
    cleanupMode: Boolean,
) {
    val initialPage = items.indexOfFirst { it.id == initialItem.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { items.size },
    )

    var chromeVisible by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    val currentItem = items.getOrNull(pagerState.currentPage)

    LaunchedEffect(currentItem?.id) {
        chromeVisible = false
        showDetails = false
    }

    fun toggleChrome() {
        chromeVisible = !chromeVisible
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (cleanupMode) Color(0xFF38100A) else Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]

            if (item.kind == MediaKind.VIDEO) {
                VideoPlayer(
                    item = item,
                    onSingleTap = ::toggleChrome,
                    onDoubleTap = { if (cleanupMode) onTrash(item) },
                )
            } else {
                ZoomableImage(
                    item = item,
                    onSingleTap = ::toggleChrome,
                    cleanupMode = cleanupMode,
                    onTrash = { onTrash(item) },
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .statusBarsPadding(),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp),
                ) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White,
                    )
                }

                if (currentItem != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp),
                    ) {
                        IconButton(onClick = { showDetails = true }) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "Полные сведения / EXIF",
                                tint = Color.White,
                            )
                        }

                        IconButton(onClick = { onTrash(currentItem) }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "В корзину",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }

        if (
            chromeVisible &&
            quickExifEnabled &&
            currentItem != null &&
            currentItem.kind != MediaKind.VIDEO
        ) {
            QuickExifOverlay(
                item = currentItem,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (showDetails && currentItem != null) {
        MediaDetailsSheet(
            item = currentItem,
            onDismiss = { showDetails = false },
        )
    }
}

@Composable
private fun VideoPlayer(
    item: MediaItem,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }

    val gestureDetector = remember(item.id) {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onSingleTap()
                    return false
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    onDoubleTap()
                    return true
                }
            },
        )
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    false
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ZoomableImage(
    item: MediaItem,
    onSingleTap: () -> Unit,
    cleanupMode: Boolean,
    onTrash: () -> Unit,
) {
    val context = LocalContext.current
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }
    var viewportSize by remember(item.id) { mutableStateOf(IntSize.Zero) }

    val imageRequest = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(4096, 4096)
            .build()
    }

    fun maxOffsets(targetScale: Float): Pair<Float, Float> {
        val viewportWidth = viewportSize.width.toFloat()
        val viewportHeight = viewportSize.height.toFloat()
        if (viewportWidth <= 0f || viewportHeight <= 0f) return 0f to 0f

        val sourceWidth = item.width.takeIf { it > 0 }?.toFloat() ?: viewportWidth
        val sourceHeight = item.height.takeIf { it > 0 }?.toFloat() ?: viewportHeight
        val fitScale = min(viewportWidth / sourceWidth, viewportHeight / sourceHeight)
        val fittedWidth = sourceWidth * fitScale
        val fittedHeight = sourceHeight * fitScale

        val maxX = ((fittedWidth * targetScale - viewportWidth) / 2f).coerceAtLeast(0f)
        val maxY = ((fittedHeight * targetScale - viewportHeight) / 2f).coerceAtLeast(0f)
        return maxX to maxY
    }

    fun clampOffsets(targetScale: Float) {
        if (targetScale <= 1.01f) {
            offsetX = 0f
            offsetY = 0f
            return
        }

        val (maxX, maxY) = maxOffsets(targetScale)
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged {
                viewportSize = it
                clampOffsets(scale)
            }
            .pointerInput(item.id, viewportSize) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var gestureActive = true

                    while (gestureActive) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        val shouldTransform = pressedCount > 1 || scale > 1.01f

                        if (shouldTransform) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 8f)
                            scale = newScale

                            if (newScale > 1.01f) {
                                offsetX += pan.x
                                offsetY += pan.y
                                clampOffsets(newScale)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }

                            event.changes.forEach { change ->
                                if (change.pressed) change.consume()
                            }
                        }

                        gestureActive = event.changes.any { it.pressed }
                    }
                }
            }
            .pointerInput(item.id, cleanupMode, viewportSize) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = {
                        if (cleanupMode) {
                            onTrash()
                        } else {
                            scale = when {
                                scale < 1.5f -> 2.5f
                                scale < 5f -> 8f
                                else -> 1f
                            }
                            clampOffsets(scale)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
        )
    }
}

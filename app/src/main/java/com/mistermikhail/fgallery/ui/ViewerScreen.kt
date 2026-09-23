package com.mistermikhail.fgallery.ui

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    initialItem: MediaItem,
    onBack: () -> Unit,
    onTrash: (MediaItem) -> Unit,
    quickExifEnabled: Boolean,
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
            .background(Color.Black),
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
                    onDoubleTap = { onTrash(item) },
                )
            } else {
                ZoomableImage(
                    item = item,
                    onSingleTap = ::toggleChrome,
                    onDoubleTap = { onTrash(item) },
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.42f)),
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
    onDoubleTap: () -> Unit,
) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(item.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)

                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { onDoubleTap() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = item.uri,
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

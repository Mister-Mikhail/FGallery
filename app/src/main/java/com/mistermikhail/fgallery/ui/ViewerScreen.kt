package com.mistermikhail.fgallery.ui

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mistermikhail.fgallery.R
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.ThumbnailCache
import kotlinx.coroutines.launch
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
    onShare: (MediaItem) -> Unit,
    onCrop: (MediaItem) -> Unit,
    onRename: (MediaItem, String) -> Unit,
    onMove: (MediaItem, String) -> Unit,
) {
    val initialPage = items.indexOfFirst { it.id == initialItem.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { items.size },
    )

    var chromeVisible by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<MediaItem?>(null) }
    var movePath by remember { mutableStateOf("") }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty() && pagerState.currentPage > items.lastIndex) {
            pagerState.scrollToPage(items.lastIndex)
        }
    }

    val currentItem = items.getOrNull(pagerState.currentPage)

    LaunchedEffect(currentItem?.id) {
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
            val activePage = page == pagerState.currentPage

            if (item.kind == MediaKind.VIDEO) {
                VideoPlayer(
                    item = item,
                    active = activePage,
                    onSingleTap = ::toggleChrome,
                    onDoubleTap = {
                        if (cleanupMode) onTrash(item)
                    },
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
                        IconButton(onClick = { onShare(currentItem) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Отправить", tint = Color.White)
                        }
                        if (currentItem.kind != MediaKind.VIDEO) {
                            IconButton(onClick = { onCrop(currentItem) }) {
                                Icon(Icons.Outlined.Crop, contentDescription = "Кадрировать", tint = Color.White)
                            }
                        }
                        IconButton(onClick = {
                            renameTarget = currentItem
                            renameText = currentItem.name
                        }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Переименовать", tint = Color.White)
                        }
                        IconButton(onClick = {
                            moveTarget = currentItem
                            movePath = currentItem.relativePath
                        }) {
                            Icon(Icons.Outlined.DriveFileMove, contentDescription = "Переместить", tint = Color.White)
                        }
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

    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Переименовать файл") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Новое имя") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = renameText.trim()
                    if (value.isNotBlank()) {
                        onRename(item, value)
                        renameTarget = null
                    }
                }) { Text("Переименовать") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Отмена") } },
        )
    }

    moveTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Переместить файл") },
            text = {
                Column {
                    Text("Папка относительно памяти телефона")
                    OutlinedTextField(
                        value = movePath,
                        onValueChange = { movePath = it },
                        singleLine = true,
                        label = { Text("Например Pictures/Travel/") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = movePath.trim()
                    if (value.isNotBlank()) {
                        onMove(item, value)
                        moveTarget = null
                    }
                }) { Text("Переместить") }
            },
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("Отмена") } },
        )
    }
}

private fun MediaItem.isLikely360Video(): Boolean {
    if (kind != MediaKind.VIDEO) return false

    val normalizedName = name.lowercase()
    val explicit360 =
        "360" in normalizedName ||
            "spherical" in normalizedName ||
            "equirect" in normalizedName ||
            "vr360" in normalizedName

    val ratio = if (height > 0) width.toFloat() / height.toFloat() else 0f
    val highResolutionEquirectangular =
        width >= 1600 &&
            height >= 700 &&
            ratio in 1.88f..2.12f

    return explicit360 || highResolutionEquirectangular
}

@Composable
private fun VideoPlayer(
    item: MediaItem,
    active: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val context = LocalContext.current
    val spherical = remember(item.id, item.width, item.height, item.name) {
        item.isLikely360Video()
    }

    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = false
            volume = 0f
        }
    }

    LaunchedEffect(active) {
        if (active) {
            player.volume = 1f
            player.play()
        } else {
            player.pause()
            player.volume = 0f
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
            val view = if (spherical) {
                LayoutInflater.from(ctx)
                    .inflate(R.layout.player_view_spherical, null, false) as PlayerView
            } else {
                PlayerView(ctx).apply {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    useController = true
                }
            }

            view.apply {
                this.player = player
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
    val animationScope = rememberCoroutineScope()

    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }

    var viewportSize by remember(item.id) { mutableStateOf(IntSize.Zero) }
    var highResRequested by remember(item.id) { mutableStateOf(false) }

    val isAnimatedGif = remember(item.name, item.mimeType) {
        item.name.endsWith(".gif", ignoreCase = true) ||
            item.mimeType.equals("image/gif", ignoreCase = true)
    }

    val decodeEdge = when {
        isAnimatedGif -> 2048
        highResRequested -> 4096
        else -> 2048
    }

    val imageRequest = remember(item.uri, decodeEdge) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(decodeEdge, decodeEdge)
            .build()
    }

    val fastPreview = remember(item.id, item.dateModifiedMillis) {
        ThumbnailCache.fileFor(
            context = context,
            item = item,
            highQuality = false,
        )
    }
    val highPreview = remember(item.id, item.dateModifiedMillis) {
        ThumbnailCache.fileFor(
            context = context,
            item = item,
            highQuality = true,
        )
    }
    val cachedPreview = when {
        highPreview.exists() -> highPreview
        fastPreview.exists() -> fastPreview
        else -> null
    }

    var sourceLoaded by remember(item.id, decodeEdge) { mutableStateOf(false) }
    val sourceAlpha by animateFloatAsState(
        targetValue = if (sourceLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "full-image-fade",
    )

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

    fun clampOffsets(targetScale: Float = scale) {
        if (targetScale <= 1.01f) {
            offsetX = 0f
            offsetY = 0f
            return
        }

        val (maxX, maxY) = maxOffsets(targetScale)
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    fun animateDoubleTapZoom() {
        if (cleanupMode) {
            onTrash()
            return
        }

        val targetScale = when {
            scale < 1.5f -> 2.5f
            scale < 5f -> 8f
            else -> 1f
        }

        if (targetScale > 1.25f) {
            highResRequested = true
        }

        val startScale = scale
        val startOffsetX = offsetX
        val startOffsetY = offsetY

        animationScope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 240,
                    easing = FastOutSlowInEasing,
                ),
            ) { progress, _ ->
                scale = startScale + (targetScale - startScale) * progress
                offsetX = startOffsetX * (1f - progress)
                offsetY = startOffsetY * (1f - progress)
                clampOffsets(scale)
            }

            scale = targetScale
            if (targetScale <= 1.01f) {
                offsetX = 0f
                offsetY = 0f
            } else {
                clampOffsets(targetScale)
            }
        }
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
                    var transforming = false
                    var accumulatedPan = Offset.Zero

                    while (gestureActive) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()

                        accumulatedPan += pan

                        val multiTouch = pressedCount > 1
                        val realPan =
                            scale > 1.01f &&
                                accumulatedPan.getDistance() > viewConfiguration.touchSlop

                        if (multiTouch || realPan || transforming) {
                            transforming = true

                            val newScale = (scale * zoom).coerceIn(1f, 8f)
                            if (newScale > 1.25f) {
                                highResRequested = true
                            }

                            scale = newScale

                            if (newScale > 1.01f) {
                                val (maxX, maxY) = maxOffsets(newScale)
                                offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
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
                    onTap = {
                        onSingleTap()
                    },
                    onDoubleTap = {
                        animateDoubleTapZoom()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            contentAlignment = Alignment.Center,
        ) {
            if (cachedPreview != null) {
                AsyncImage(
                    model = cachedPreview,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                onSuccess = { sourceLoaded = true },
                onLoading = { sourceLoaded = false },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (cachedPreview == null) 1f else sourceAlpha
                    },
            )
        }
    }
}

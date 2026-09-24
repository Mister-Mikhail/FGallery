package com.mistermikhail.fgallery.ui

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.mistermikhail.fgallery.R
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.ThumbnailCache
import kotlinx.coroutines.delay
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

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
    onMove: (MediaItem) -> Unit,
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
    var videoMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty() && pagerState.currentPage > items.lastIndex) {
            pagerState.scrollToPage(items.lastIndex)
        }
    }

    val currentItem = items.getOrNull(pagerState.currentPage)
    val currentIsVideo = currentItem?.kind == MediaKind.VIDEO

    LaunchedEffect(currentItem?.id) {
        showDetails = false
        videoMenuExpanded = false
    }

    // Video controls and the compact file-actions button use the same short lifetime.
    LaunchedEffect(chromeVisible, currentIsVideo, videoMenuExpanded, currentItem?.id) {
        if (chromeVisible && currentIsVideo && !videoMenuExpanded) {
            delay(2_000L)
            chromeVisible = false
        }
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
                TiledZoomableImage(
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
            if (currentIsVideo) {
                // Video mode: no wide top toolbar. Keep only back + compact actions menu.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White,
                        )
                    }

                    Box(
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        IconButton(onClick = { videoMenuExpanded = true }) {
                            Icon(
                                Icons.Outlined.Menu,
                                contentDescription = "Действия с файлом",
                                tint = Color.White,
                            )
                        }

                        if (currentItem != null) {
                            DropdownMenu(
                                expanded = videoMenuExpanded,
                                onDismissRequest = { videoMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Отправить") },
                                    onClick = {
                                        videoMenuExpanded = false
                                        onShare(currentItem)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Переименовать") },
                                    onClick = {
                                        videoMenuExpanded = false
                                        renameTarget = currentItem
                                        renameText = currentItem.name
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Переместить") },
                                    onClick = {
                                        videoMenuExpanded = false
                                        onMove(currentItem)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Сведения") },
                                    onClick = {
                                        videoMenuExpanded = false
                                        showDetails = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("В корзину") },
                                    onClick = {
                                        videoMenuExpanded = false
                                        onTrash(currentItem)
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
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
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = "Отправить",
                                    tint = Color.White,
                                )
                            }
                            IconButton(onClick = { onCrop(currentItem) }) {
                                Icon(
                                    Icons.Outlined.Crop,
                                    contentDescription = "Кадрировать",
                                    tint = Color.White,
                                )
                            }
                            IconButton(
                                onClick = {
                                    renameTarget = currentItem
                                    renameText = currentItem.name
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "Переименовать",
                                    tint = Color.White,
                                )
                            }
                            IconButton(
                                onClick = { onMove(currentItem) },
                            ) {
                                Icon(
                                    Icons.Outlined.DriveFileMove,
                                    contentDescription = "Переместить",
                                    tint = Color.White,
                                )
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
                TextButton(
                    onClick = {
                        val value = renameText.trim()
                        if (value.isNotBlank()) {
                            onRename(item, value)
                            renameTarget = null
                        }
                    },
                ) {
                    Text("Переименовать")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Отмена")
                }
            },
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
                controllerShowTimeoutMs = 2_000
                controllerAutoShow = false
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
        update = { view ->
            view.controllerShowTimeoutMs = 2_000
        },
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    )
}

@Composable
private fun TiledZoomableImage(
    item: MediaItem,
    onSingleTap: () -> Unit,
    cleanupMode: Boolean,
    onTrash: () -> Unit,
) {
    val context = LocalContext.current
    var doubleTapStage by remember(item.id) { mutableIntStateOf(0) }

    val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 8f),
    )
    val imageState = rememberZoomableImageState(zoomableState)

    val cachedPreview = remember(item.id, item.dateModifiedMillis) {
        val highPreview = ThumbnailCache.fileFor(
            context = context,
            item = item,
            highQuality = true,
        )
        val fastPreview = ThumbnailCache.fileFor(
            context = context,
            item = item,
            highQuality = false,
        )

        when {
            highPreview.exists() -> highPreview
            fastPreview.exists() -> fastPreview
            else -> null
        }
    }

    val tiledAlpha by animateFloatAsState(
        targetValue = if (imageState.isImageDisplayed) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "tiled-image-fade",
    )

    val doubleClick = remember(item.id, cleanupMode) {
        DoubleClickToZoomListener { state, centroid ->
            if (cleanupMode) {
                onTrash()
                return@DoubleClickToZoomListener
            }

            when (doubleTapStage) {
                0 -> {
                    state.zoomTo(
                        zoomFactor = 2.5f,
                        centroid = centroid,
                        animationSpec = tween(durationMillis = 240),
                    )
                    doubleTapStage = 1
                }

                1 -> {
                    state.zoomTo(
                        zoomFactor = 8f,
                        centroid = centroid,
                        animationSpec = tween(durationMillis = 260),
                    )
                    doubleTapStage = 2
                }

                else -> {
                    state.resetZoom(
                        animationSpec = tween(durationMillis = 260),
                    )
                    doubleTapStage = 0
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
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

        ZoomableAsyncImage(
            model = item.uri,
            contentDescription = item.name,
            state = imageState,
            contentScale = ContentScale.Fit,
            onClick = { onSingleTap() },
            onDoubleClick = doubleClick,
            alpha = if (cachedPreview == null) 1f else tiledAlpha,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

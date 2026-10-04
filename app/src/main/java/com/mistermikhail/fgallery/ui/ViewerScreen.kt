package com.mistermikhail.fgallery.ui

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.exoplayer.video.spherical.ZoomableSphericalView
import androidx.media3.ui.PlayerControlView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.Player
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
    active: Boolean = true,
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
    var chromeEpoch by remember { mutableIntStateOf(0) }
    var showDetails by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var videoMenuExpanded by remember { mutableStateOf(false) }
    var sphericalOverrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty() && pagerState.currentPage > items.lastIndex) {
            pagerState.scrollToPage(items.lastIndex)
        }
    }

    val currentItem = items.getOrNull(pagerState.currentPage)
    val currentIsVideo = currentItem?.kind == MediaKind.VIDEO

    LaunchedEffect(currentItem?.id, currentIsVideo) {
        showDetails = false
        videoMenuExpanded = false
        if (currentIsVideo) {
            chromeVisible = true
            chromeEpoch += 1
        }
    }

    // One clock owns both PlayerView controls and the Compose file chrome.
    LaunchedEffect(chromeVisible, currentIsVideo, videoMenuExpanded, chromeEpoch) {
        if (chromeVisible && currentIsVideo && !videoMenuExpanded) {
            delay(1_500L)
            chromeVisible = false
        }
    }

    fun toggleChrome() {
        if (currentIsVideo) {
            if (chromeVisible) {
                chromeVisible = false
            } else {
                chromeVisible = true
                chromeEpoch += 1
            }
        } else {
            chromeVisible = !chromeVisible
        }
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
            val activePage = active && page == pagerState.currentPage

            if (item.kind == MediaKind.VIDEO) {
                VideoPlayer(
                    item = item,
                    active = activePage,
                    sphericalOverride = sphericalOverrides[item.uriKey],
                    controlsVisible = activePage && chromeVisible,
                    onSingleTap = ::toggleChrome,
                    onDoubleTap = {
                        if (cleanupMode) onTrash(item)
                    },
                )
            } else if (item.kind == MediaKind.PDF) {
                PdfViewer(item, ::toggleChrome)
            } else if (item.kind == MediaKind.SVG) {
                SvgViewer(item, ::toggleChrome)
            } else {
                TiledZoomableImage(
                    item = item,
                    onSingleTap = ::toggleChrome,
                    cleanupMode = cleanupMode,
                    onTrash = { onTrash(item) },
                    active = activePage,
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
                                    text = { Text(if (sphericalOverrides[currentItem.uriKey] ?: currentItem.isLikely360Video()) "Обычный просмотр" else "Просмотр 360°") },
                                    onClick = {
                                        val sphericalNow = sphericalOverrides[currentItem.uriKey] ?: currentItem.isLikely360Video()
                                        sphericalOverrides = sphericalOverrides + (currentItem.uriKey to !sphericalNow)
                                        videoMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Кадрировать видео") },
                                    onClick = { videoMenuExpanded = false; onCrop(currentItem) },
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
                            if (currentItem.kind != MediaKind.PDF && currentItem.kind != MediaKind.SVG) IconButton(onClick = { onCrop(currentItem) }) {
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
    sphericalOverride: Boolean?,
    controlsVisible: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestActive by rememberUpdatedState(active)
    val latestSingleTap by rememberUpdatedState(onSingleTap)
    val latestDoubleTap by rememberUpdatedState(onDoubleTap)
    var sphericalView by remember(item.uri) { mutableStateOf<ZoomableSphericalView?>(null) }
    val spherical = remember(item.id, item.width, item.height, item.name, sphericalOverride) {
        sphericalOverride ?: item.isLikely360Video()
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
        if (active && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            sphericalView?.onResume()
            player.volume = 1f
            player.play()
        } else {
            sphericalView?.onPause()
            player.pause()
            player.volume = 0f
        }
    }

    val gestureDetector = remember(item.id) {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    latestSingleTap()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    latestDoubleTap()
                    return true
                }
            },
        )
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { player.pause(); sphericalView?.onPause() }
                Lifecycle.Event.ON_RESUME -> if (latestActive) { player.volume = 1f; player.play(); sphericalView?.onResume() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sphericalView?.onPause()
            player.release()
        }
    }

    if (spherical) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    val surface = ZoomableSphericalView(ctx)
                    sphericalView = surface
                    surface.setUseSensorRotation(active)
                    surface.setTapCallbacks({ latestSingleTap() }, { latestDoubleTap() })
                    player.setVideoFrameMetadataListener(surface.videoFrameMetadataListener)
                    player.setCameraMotionListener(surface.cameraMotionListener)
                    surface.addVideoSurfaceListener(object : ZoomableSphericalView.VideoSurfaceListener {
                        override fun onVideoSurfaceCreated(videoSurface: android.view.Surface) { player.setVideoSurface(videoSurface) }
                        override fun onVideoSurfaceDestroyed(videoSurface: android.view.Surface) { player.clearVideoSurface(videoSurface) }
                    })
                    addView(surface, FrameLayout.LayoutParams(-1, -1))
                    val controls = PlayerControlView(ctx).apply { this.player = player; showTimeoutMs = 0 }
                    addView(controls, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
                    if (active && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) surface.onResume() else surface.onPause()
                }
            },
            update = { frame ->
                val surface = frame.getChildAt(0) as ZoomableSphericalView
                surface.setUseSensorRotation(active)
                val controls = frame.getChildAt(1) as PlayerControlView
                if (controlsVisible) controls.show() else controls.hide()
            },
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        )
    } else {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    controllerShowTimeoutMs = 0
                    controllerAutoShow = false
                    controllerHideOnTouch = false
                    setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); true }
                }
            },
            update = { view -> if (controlsVisible) view.showController() else view.hideController() },
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        )
    }

}

internal val ViewerZoomRange = SemanticsPropertyKey<Pair<Float, Float>>("ViewerZoomRange")
internal val ViewerImageReady = SemanticsPropertyKey<Boolean>("ViewerImageReady")

@Composable
internal fun TiledZoomableImage(
    item: MediaItem,
    onSingleTap: () -> Unit,
    cleanupMode: Boolean,
    onTrash: () -> Unit,
    active: Boolean = true,
) {
    val context = LocalContext.current
    var imageFailed by remember(item.uri) { mutableStateOf(false) }
    var fullSource by remember(item.uri) { mutableStateOf<java.io.File?>(null) }
    var fullError by remember(item.uri) { mutableStateOf<String?>(null) }
    val needsFullSource = item.kind == MediaKind.RAW || com.mistermikhail.fgallery.data.TiffImages.isTiff(item.name, item.mimeType)
    LaunchedEffect(item.uri, active) {
        if (needsFullSource && active && fullSource == null) {
            try {
                fullSource = com.mistermikhail.fgallery.data.FullResolutionFiles.prepare(context, item)
                imageFailed = false
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { fullError = e.localizedMessage ?: "Не удалось прочитать исходные данные" }
        }
    }
    var maximum by remember(item.uri) { mutableFloatStateOf(8f) }

    val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = maximum),
    )
    val imageState = rememberZoomableImageState(zoomableState)
    LaunchedEffect(zoomableState) {
        snapshotFlow { zoomableState.contentTransformation.scaleMetadata.initialScale }.collect { fit ->
            val scale = maxOf(fit.scaleX, fit.scaleY)
            if (scale.isFinite() && scale > 0f) maximum = maxOf(8f, scale * 8f)
        }
    }

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


    val doubleClick = remember(item.id, cleanupMode) {
        DoubleClickToZoomListener { _, centroid ->
            val state = zoomableState
            if (cleanupMode) {
                onTrash()
                return@DoubleClickToZoomListener
            }

            if ((needsFullSource && fullSource == null) || !imageState.isImageDisplayed || !state.contentTransformation.isSpecified || state.isAnimationRunning) return@DoubleClickToZoomListener
            val transformation = state.contentTransformation
            val fit = maxOf(transformation.scaleMetadata.initialScale.scaleX, transformation.scaleMetadata.initialScale.scaleY)
            val current = maxOf(transformation.scale.scaleX, transformation.scale.scaleY)
            val target = nextDoubleTapZoom(current, fit, state.zoomSpec.maximum.factor)
            if (target <= fit * 1.001f) state.resetZoom(animationSpec = tween(260))
            else state.zoomTo(zoomFactor = target, centroid = centroid, animationSpec = tween(260))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (cachedPreview != null && (!imageState.isImageDisplayed || (needsFullSource && fullSource == null))) {
            AsyncImage(
                model = cachedPreview,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (imageFailed && cachedPreview == null) Text("Не удалось открыть изображение. Формат может не поддерживаться декодером устройства.", color = Color.White, modifier = Modifier.padding(24.dp))
        if (needsFullSource && fullSource == null) {
            if (fullError == null && active) CircularProgressIndicator(Modifier.align(Alignment.BottomCenter).padding(24.dp))
            fullError?.let { Text("Не удалось загрузить полный размер: $it", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)) }
        }
        ZoomableAsyncImage(
            model = coil3.request.ImageRequest.Builder(context).data(fullSource ?: if (needsFullSource) cachedPreview else if (imageFailed && cachedPreview != null) cachedPreview else item.uri)
                .memoryCacheKey("${item.uri}/${item.dateModifiedMillis}/${if (fullSource != null) "full" else if (imageFailed || needsFullSource) "fallback" else "source"}")
                .listener(onError = { _, _ -> imageFailed = true })
                .build(),
            contentDescription = item.name,
            state = imageState,
            contentScale = ContentScale.Fit,
            onClick = { onSingleTap() },
            onDoubleClick = doubleClick,
            alpha = 1f,
            modifier = Modifier.fillMaxSize().semantics {
                stateDescription = "Zoom ${(zoomableState.contentTransformation.scaleMetadata.userZoom * 100).toInt()}%"
                val initial = zoomableState.contentTransformation.scaleMetadata.initialScale
                this[ViewerZoomRange] = maxOf(initial.scaleX, initial.scaleY) to zoomableState.zoomSpec.maximum.factor
                this[ViewerImageReady] = imageState.isImageDisplayed && (!needsFullSource || fullSource != null)
            }.testTag("viewer-image"),
        )
    }
}

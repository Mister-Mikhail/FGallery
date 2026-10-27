package com.mistermikhail.fgallery.ui

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.common.util.UnstableApi
import androidx.compose.runtime.mutableLongStateOf
import kotlin.math.abs
import kotlin.math.roundToInt
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
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.graphics.graphicsLayer
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
    onRestore: (MediaItem) -> Unit = {},
    editedPreviewUri: android.net.Uri? = null,
    quickExifEnabled: Boolean,
    cleanupMode: Boolean,
    onShare: (MediaItem) -> Unit,
    onCrop: (MediaItem) -> Unit,
    onRename: (MediaItem, String) -> Unit,
    onMove: (MediaItem) -> Unit,
    deletingItemUri: String? = null,
    recycleBinMode: Boolean = false,
    zoomSecondPower: Float = .85f,
) {
    val initialPage = items.indexOfFirst { it.uriKey == initialItem.uriKey }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { items.size },
    )

    var chromeVisible by remember { mutableStateOf(false) }
    var chromeEpoch by remember { mutableIntStateOf(0) }
    var showDetails by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    var videoMenuExpanded by remember { mutableStateOf(false) }
    var videoPlaying by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(initialItem.uriKey) {
        val target = items.indexOfFirst { it.uriKey == initialItem.uriKey }
        if (target >= 0 && target != pagerState.currentPage) pagerState.scrollToPage(target)
    }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty() && pagerState.currentPage > items.lastIndex) {
            pagerState.scrollToPage(items.lastIndex)
        }
    }

    val currentItem = items.getOrNull(pagerState.currentPage)
    val currentIsVideo = currentItem?.kind == MediaKind.VIDEO
    val deletingCurrent = currentItem?.uriKey == deletingItemUri && deletingItemUri != null
    val mediaAlpha by animateFloatAsState(if (deletingCurrent) .45f else 1f, tween(60), label = "trash-fade")

    LaunchedEffect(currentItem?.uriKey, currentIsVideo) {
        showDetails = false
        videoMenuExpanded = false
        if (currentIsVideo) {
            chromeVisible = true
            chromeEpoch += 1
        }
    }

    // One clock owns both PlayerView controls and the Compose file chrome.
    LaunchedEffect(chromeVisible, currentIsVideo, videoMenuExpanded, chromeEpoch, videoPlaying) {
        if (chromeVisible && currentIsVideo && videoPlaying && !videoMenuExpanded) {
            delay(3_000L)
            if (videoPlaying) chromeVisible = false
        }
    }

    fun toggleChrome() {
        if (currentIsVideo) {
            if (!videoPlaying) { chromeVisible = true; chromeEpoch++; return }
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
            .background(if (cleanupMode && !recycleBinMode) Color(0xFF38100A) else Color.Black)
            .semantics { this[ViewerCurrentUri] = currentItem?.uriKey.orEmpty() }
            .testTag("viewer-root"),
    ) {
        HorizontalPager(
            state = pagerState,
            key = { items[it].uriKey },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = mediaAlpha },
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            val activePage = active && page == pagerState.currentPage

            if (item.kind == MediaKind.VIDEO) {
                VideoPlayer(
                    item = item,
                    active = activePage,
                    controlsVisible = activePage && chromeVisible,
                    onPlayingChanged = { playing ->
                        videoPlaying = playing
                        if (!playing && activePage) { chromeVisible = true; chromeEpoch++ }
                    },
                    onSingleTap = ::toggleChrome,
                    onDoubleTap = {
                        if (cleanupMode && !recycleBinMode) onTrash(item)
                    },
                    topBar = {
                        VideoTopBar(
                            expanded = videoMenuExpanded,
                            onMenuExpandedChange = { videoMenuExpanded = it },
                            onBack = onBack,
                            onShare = { onShare(item) },
                            onCrop = { onCrop(item) },
                            onRename = { renameTarget = item },
                            onMove = { onMove(item) },
                            onDetails = { showDetails = true },
                            onTrash = { onTrash(item) },
                            onRestore = { onRestore(item) },
                            recycleBinMode = recycleBinMode,
                        )
                    },
                )
            } else if (item.kind == MediaKind.PDF) {
                PdfViewer(item, ::toggleChrome, onDoubleTap = if (cleanupMode && !recycleBinMode) ({ onTrash(item) }) else null, zoomSecondPower = zoomSecondPower)
            } else if (item.kind == MediaKind.SVG) {
                SvgViewer(item, ::toggleChrome, onDoubleTap = if (cleanupMode && !recycleBinMode) ({ onTrash(item) }) else null, zoomSecondPower = zoomSecondPower)
            } else {
                TiledZoomableImage(
                    item = item,
                    onSingleTap = ::toggleChrome,
                    cleanupMode = cleanupMode && !recycleBinMode,
                    onTrash = { onTrash(item) },
                    active = activePage,
                    zoomSecondPower = zoomSecondPower,
                )
            }
        }

        if (editedPreviewUri != null && currentItem?.uriKey == initialItem.uriKey) {
            AsyncImage(
                model = editedPreviewUri,
                contentDescription = "Результат кадрирования",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        AnimatedVisibility(
            visible = chromeVisible && !currentIsVideo,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            if (!currentIsVideo) {
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
                            if (!recycleBinMode && currentItem.kind != MediaKind.PDF && currentItem.kind != MediaKind.SVG) IconButton(onClick = { onCrop(currentItem) }) {
                                Icon(
                                    Icons.Outlined.Crop,
                                    contentDescription = "Кадрировать",
                                    tint = Color.White,
                                )
                            }
                            if (!recycleBinMode) IconButton(
                                onClick = {
                                    renameTarget = currentItem
                                                                    },
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "Переименовать",
                                    tint = Color.White,
                                )
                            }
                            if (!recycleBinMode) IconButton(
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
                            if (recycleBinMode) IconButton(onClick = { onRestore(currentItem) }) {
                                Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Восстановить", tint = Color.White)
                            }
                            if (!recycleBinMode) IconButton(onClick = { onTrash(currentItem) }) {
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
            !currentIsVideo &&
            currentItem != null
        ) {
            QuickExifOverlay(
                item = currentItem,
                modifier = if (currentIsVideo) Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
                    else Modifier.align(Alignment.BottomCenter),
            )
        }
        if (deletingCurrent) CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center).testTag("viewer-trash-progress"),
            color = Color.White,
        )
    }

    if (showDetails && currentItem != null) {
        MediaDetailsSheet(
            item = currentItem,
            onDismiss = { showDetails = false },
        )
    }

    renameTarget?.let { item ->
        RenameDialog(
            items = listOf(item),
            onDismiss = { renameTarget = null },
            onConfirm = { plan ->
                renameTarget = null
                plan.forEach { (source, name) -> onRename(source, name) }
            },
        )
    }

}

@Composable
@OptIn(UnstableApi::class)
private fun VideoPlayer(
    item: MediaItem,
    active: Boolean,
    controlsVisible: Boolean,
    onPlayingChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    topBar: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestActive by rememberUpdatedState(active)
    val latestSingleTap by rememberUpdatedState(onSingleTap)
    val latestDoubleTap by rememberUpdatedState(onDoubleTap)
    val latestPlayingChanged by rememberUpdatedState(onPlayingChanged)
    var sphericalView by remember(item.uri) { mutableStateOf<ZoomableSphericalView?>(null) }
    val detected360 by androidx.compose.runtime.produceState<Boolean?>(null, item.uriKey, item.dateModifiedMillis) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.mistermikhail.fgallery.data.VideoProjection.isSpherical(context, item)
        }
    }
    var format360 by remember(item.uriKey) { mutableStateOf(false) }
    var firstFrameRendered by remember(item.uriKey) { mutableStateOf(false) }
    val spherical = detected360 == true || format360
    val latestDetection by rememberUpdatedState(detected360)

    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = false
            volume = 0f
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { firstFrameRendered = true }
            override fun onIsPlayingChanged(isPlaying: Boolean) { latestPlayingChanged(isPlaying) }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                format360 = tracks.groups.any { group ->
                    group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO &&
                        (0 until group.length).any { group.isTrackSelected(it) && group.getTrackFormat(it).projectionData != null }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    var startedPlayback by remember(item.uri) { mutableStateOf(false) }
    var wasPlayingAtLifecyclePause by remember(item.uri) { mutableStateOf(false) }

    LaunchedEffect(active, detected360, spherical) {
        if (active && detected360 != null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            sphericalView?.onResume()
            player.volume = 1f
            if (!startedPlayback) { startedPlayback = true; player.play() }
        } else {
            sphericalView?.onPause()
            player.pause()
            player.volume = 0f
            if (!active) startedPlayback = false
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPlayingAtLifecyclePause = player.isPlaying
                    player.pause()
                    sphericalView?.onPause()
                }
                Lifecycle.Event.ON_RESUME -> if (latestActive && latestDetection != null) {
                    player.volume = 1f
                    if (wasPlayingAtLifecyclePause) player.play()
                    sphericalView?.onResume()
                }
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

    val activity = context as? Activity
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember(audio) { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val originalBrightness = remember(item.uriKey) { activity?.window?.attributes?.screenBrightness ?: -1f }
    var adjustment by remember(item.uriKey) { mutableStateOf<Pair<VideoAdjustmentSide, Float>?>(null) }
    val edgePx = with(LocalDensity.current) { 28.dp.toPx() }
    DisposableEffect(activity, item.uriKey) {
        onDispose {
            activity?.window?.let { window ->
                val params = window.attributes
                params.screenBrightness = originalBrightness
                window.attributes = params
            }
        }
    }
    LaunchedEffect(adjustment) {
        if (adjustment != null) {
            delay(900L)
            adjustment = null
        }
    }

    val videoModifier = Modifier.fillMaxSize()
        .semantics {
            this[ViewerVideoReady] = firstFrameRendered
            this[ViewerCurrentUri] = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
        }.testTag("viewer-video")

    Box(
        videoModifier.pointerInput(active, item.uriKey) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!active) return@awaitEachGesture
                val side = videoGestureSide(down.position.x, down.position.y,
                    size.width.toFloat(), size.height.toFloat(), edgePx)
                    ?: return@awaitEachGesture
                var initialLevel = when (side) {
                    VideoAdjustmentSide.VOLUME ->
                        audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                    VideoAdjustmentSide.BRIGHTNESS ->
                        activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: .5f
                }
                initialLevel = initialLevel.coerceIn(0f, 1f)
                var vertical = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (change.isConsumed && !vertical) break
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (!vertical && isUnambiguousVertical(dx, dy, viewConfiguration.touchSlop))
                        vertical = true
                    if (!vertical && abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy))
                        break // HorizontalPager retains horizontal file swiping.
                    if (vertical) {
                        val fraction = videoAdjustmentFraction(initialLevel, dy, size.height.toFloat())
                        if (side == VideoAdjustmentSide.VOLUME) {
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                                (fraction * maxVolume).roundToInt().coerceIn(0, maxVolume), 0)
                        } else {
                            activity?.window?.let { window ->
                                val params = window.attributes
                                params.screenBrightness = fraction.coerceAtLeast(.01f)
                                window.attributes = params
                            }
                        }
                        adjustment = side to fraction
                        change.consume()
                    }
                }
            }
        },
    ) {
        if (spherical) {
            AndroidView(
                factory = { ctx ->
                    ZoomableSphericalView(ctx).apply {
                        sphericalView = this
                        setUseSensorRotation(active)
                        setTapCallbacks({ latestSingleTap() }, { latestDoubleTap() })
                        player.setVideoFrameMetadataListener(videoFrameMetadataListener)
                        player.setCameraMotionListener(cameraMotionListener)
                        addVideoSurfaceListener(object : ZoomableSphericalView.VideoSurfaceListener {
                            override fun onVideoSurfaceCreated(videoSurface: android.view.Surface) {
                                player.setVideoSurface(videoSurface)
                            }
                            override fun onVideoSurfaceDestroyed(videoSurface: android.view.Surface) {
                                player.clearVideoSurface(videoSurface)
                            }
                        })
                        if (active && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onResume()
                    }
                },
                update = { view -> view.setUseSensorRotation(active) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ContentFrame(player, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Box(
                Modifier.fillMaxSize().pointerInput(item.uriKey) {
                    detectTapGestures(
                        onTap = { latestSingleTap() },
                        onDoubleTap = { latestDoubleTap() },
                    )
                },
            )
        }

        UnifiedVideoChrome(
            player = player, filename = item.name,
            visible = controlsVisible, topBar = topBar,
        )

        adjustment?.let { (side, amount) ->
            VideoAdjustmentIndicator(
                side = side, value = amount,
                modifier = Modifier.align(if (side == VideoAdjustmentSide.BRIGHTNESS) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 26.dp),
            )
        }
    }

}

internal val ViewerZoomRange = SemanticsPropertyKey<Pair<Float, Float>>("ViewerZoomRange")
internal val ViewerImageReady = SemanticsPropertyKey<Boolean>("ViewerImageReady")
internal val ViewerCurrentUri = SemanticsPropertyKey<String>("ViewerCurrentUri")
internal val ViewerVideoReady = SemanticsPropertyKey<Boolean>("ViewerVideoReady")

@Composable
internal fun TiledZoomableImage(
    item: MediaItem,
    onSingleTap: () -> Unit,
    cleanupMode: Boolean,
    onTrash: () -> Unit,
    active: Boolean = true,
    zoomSecondPower: Float = .85f,
) {
    val context = LocalContext.current
    var imageFailed by remember(item.uri, item.dateModifiedMillis, item.sizeBytes) { mutableStateOf(false) }
    var fullSource by remember(item.uri, item.dateModifiedMillis, item.sizeBytes) { mutableStateOf(com.mistermikhail.fgallery.data.FullResolutionFiles.cached(context, item)) }
    var fullDecoded by remember(item.uri, item.dateModifiedMillis, item.sizeBytes) { mutableStateOf(false) }
    var fullError by remember(item.uri, item.dateModifiedMillis, item.sizeBytes) { mutableStateOf<String?>(null) }
    val needsFullSource = item.kind == MediaKind.RAW || com.mistermikhail.fgallery.data.TiffImages.isTiff(item.name, item.mimeType)
    var cachedPreview by remember(item.uri, item.dateModifiedMillis, item.sizeBytes) {
        mutableStateOf(ThumbnailCache.cached(context, item))
    }
    LaunchedEffect(item.uri, item.dateModifiedMillis, item.sizeBytes, active) {
        if (needsFullSource && active && fullSource == null) {
            try {
                if (cachedPreview == null) cachedPreview = ThumbnailCache.ensure(context, item)
                if (cachedPreview == null && item.kind == MediaKind.RAW) {
                    cachedPreview = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        ThumbnailCache.rawPreview(context, item)?.let { bitmap ->
                            try {
                                val file = ThumbnailCache.fileFor(context, item)
                                file.parentFile?.mkdirs()
                                val temp = java.io.File.createTempFile("raw_fast_", ".png", file.parentFile)
                                try {
                                    check(com.mistermikhail.fgallery.data.TiffNative.writeBitmapPng(bitmap, temp.path))
                                    check(temp.renameTo(file)); file
                                } finally { temp.delete() }
                            } finally { bitmap.recycle() }
                        }
                    }
                }
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




    var previousTapTarget by remember(item.uri) { mutableFloatStateOf(0f) }
    val doubleClick = remember(item.id, cleanupMode, zoomSecondPower) {
        DoubleClickToZoomListener { _, centroid ->
            val state = zoomableState
            if (cleanupMode) {
                onTrash()
                return@DoubleClickToZoomListener
            }

            if ((needsFullSource && !fullDecoded) || !imageState.isImageDisplayed || !state.contentTransformation.isSpecified) return@DoubleClickToZoomListener
            val transformation = state.contentTransformation
            val fit = maxOf(transformation.scaleMetadata.initialScale.scaleX, transformation.scaleMetadata.initialScale.scaleY)
            // Second double taps must not be silently discarded while the first zoom
            // animation completes. Advance from its target, not the intermediate scale.
            val current = if (state.isAnimationRunning && previousTapTarget > 0f) {
                previousTapTarget
            } else maxOf(transformation.scale.scaleX, transformation.scale.scaleY)
            val target = nextDoubleTapZoom(current, fit, maxOf(8f, fit * 8f), smallImage = fit * 2f >= 1f, power = zoomSecondPower)
            previousTapTarget = target
            if (target <= fit * 1.001f) state.resetZoom(animationSpec = tween(260))
            else state.zoomTo(zoomFactor = target, centroid = centroid, animationSpec = tween(260))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (cachedPreview != null && !imageState.isImageDisplayed) {
            AsyncImage(
                model = cachedPreview,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        ZoomableAsyncImage(
            model = coil3.request.ImageRequest.Builder(context).data(fullSource ?: if (needsFullSource) cachedPreview else if (imageFailed && cachedPreview != null) cachedPreview else item.uri)
                .memoryCacheKey("${item.uri}/${item.dateModifiedMillis}/${if (fullSource != null) "full" else if (imageFailed || needsFullSource) "fallback" else "source"}")
                .listener(onError = { _, _ -> imageFailed = true }, onSuccess = { request, _ -> if (fullSource != null && request.data == fullSource) fullDecoded = true })
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
                val fit = maxOf(initial.scaleX, initial.scaleY)
                this[ViewerZoomRange] = fit to maxOf(8f, fit * 8f)
                this[ViewerImageReady] = imageState.isImageDisplayed && (!needsFullSource || fullDecoded)
            }.testTag("viewer-image"),
        )
        if (imageState.isImageDisplayed && (!needsFullSource || fullDecoded)) {
            val transformation = zoomableState.contentTransformation
            val initial = transformation.scaleMetadata.initialScale
            val fitted = maxOf(initial.scaleX, initial.scaleY)
            val currentScale = maxOf(transformation.scale.scaleX, transformation.scale.scaleY)
            if (transformation.isSpecified && fitted.isFinite() && fitted > 0f && currentScale.isFinite()) {
                val maximumScale = maxOf(8f, fitted * 8f)
                Text(
                    text = "сейчас ${(currentScale * 100).toInt()}% · максимум ${(maximumScale * 100).toInt()}%",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 54.dp)
                        .background(Color.Black.copy(alpha = .68f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("zoom-diagnostic"),
                )
            }
        }
        if (imageFailed && cachedPreview == null) Text("Не удалось открыть изображение. Формат может не поддерживаться декодером устройства.", color = Color.White, modifier = Modifier.padding(24.dp))
        if (needsFullSource && !fullDecoded) {
            if (fullError == null && active) CircularProgressIndicator(Modifier.align(Alignment.BottomCenter).padding(24.dp))
            fullError?.let { Text("Не удалось загрузить полный размер: $it", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)) }
        }
    }
}

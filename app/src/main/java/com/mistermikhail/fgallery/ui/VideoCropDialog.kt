package com.mistermikhail.fgallery.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.effect.Crop
import androidx.media3.transformer.*
import androidx.media3.transformer.Composition
import com.mistermikhail.fgallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun VideoCropDialog(item: MediaItem, onDismiss: () -> Unit, onExported: (Uri, Boolean) -> Unit) {
    val context = LocalContext.current
    var window by remember(item.uri) { mutableStateOf(TrimWindow()) }
    var crop by remember(item.uri) { mutableStateOf(CropBounds()) }
    var exporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var transformer by remember { mutableStateOf<Transformer?>(null) }
    var output by remember { mutableStateOf<File?>(null) }
    var clipDuration by remember { mutableLongStateOf(item.durationMillis) }
    var shownMillis by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var aspect by remember { mutableFloatStateOf(item.aspectRatio) }
    var previewReady by remember { mutableStateOf(false) }
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            setSeekParameters(SeekParameters.EXACT)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    this@apply.duration.takeIf { it > 0 }?.let { clipDuration = it }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onRenderedFirstFrame() { previewReady = true }
                override fun onVideoSizeChanged(size: VideoSize) {
                    if (size.height > 0 && size.width > 0) aspect = size.width.toFloat() * size.pixelWidthHeightRatio / size.height
                }
                override fun onPlayerError(failure: androidx.media3.common.PlaybackException) { error = "Не удалось открыть видео" }
            })
            setMediaItem(PlayerMediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = false
        }
    }
    val frames by produceState<List<Bitmap>>(emptyList(), item.uri) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val images = mutableListOf<Bitmap>()
            try {
                retriever.setDataSource(context, item.uri)
                val length = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: item.durationMillis
                if (length > 0) {
                    for (index in 0..7) {
                        ensureActive()
                        runCatching { retriever.getScaledFrameAtTime((length * index / 7).coerceAtMost(length - 1) * 1000,
                            MediaMetadataRetriever.OPTION_CLOSEST, 160, 100) }.getOrNull()?.let { images.add(it) }
                    }
                }
                images
            } catch (e: kotlinx.coroutines.CancellationException) {
                images.forEach(Bitmap::recycle)
                throw e
            } catch (_: Exception) { images }
            finally { retriever.release() }
        }
    }
    // Keep frame bitmaps alive while Canvas can still be recording them; normal GC owns disposal.
    LaunchedEffect(player, playing) {
        while (playing) {
            shownMillis = player.currentPosition
            if (shownMillis >= (window.end * clipDuration).toLong()) {
                player.pause()
                player.seekTo((window.start * clipDuration).toLong())
            }
            delay(60)
        }
    }
    DisposableEffect(player) {
        onDispose { player.release(); transformer?.cancel(); output?.delete() }
    }
    fun cancel() { transformer?.cancel(); output?.delete(); exporting = false; onDismiss() }
    fun seekBoundary(next: TrimWindow, start: Boolean) {
        window = next
        player.pause()
        shownMillis = ((if (start) next.start else next.end) * clipDuration).toLong().coerceIn(0, (clipDuration - 1).coerceAtLeast(0))
        player.seekTo(shownMillis)
    }
    fun export(replace: Boolean) {
        if (clipDuration <= 0) { error = "Не удалось определить длительность видео"; return }
        exporting = true
        error = null
        try {
            val destination = File.createTempFile("video_crop_", ".mp4", context.cacheDir).also { it.delete() }
            output = destination
            val media = PlayerMediaItem.Builder().setUri(item.uri).setClippingConfiguration(
                PlayerMediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs((clipDuration * window.start).toLong())
                    .setEndPositionMs((clipDuration * window.end).toLong())
                    .build()
            ).build()
            val effects = Effects(emptyList(), listOf(Crop(
                crop.left * 2f - 1f, crop.right * 2f - 1f,
                1f - crop.bottom * 2f, 1f - crop.top * 2f,
            )))
            val edited = EditedMediaItem.Builder(media).setEffects(effects).build()
            val exporter = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        exporting = false
                        transformer = null
                        // Ownership transfers to the save flow; do not delete on dialog disposal.
                        output = null
                        onExported(Uri.fromFile(destination), replace)
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        exporting = false
                        destination.delete()
                        error = "Экспорт не завершён: ${exportException.localizedMessage}. Оригинал не изменён."
                    }
                }).build()
            transformer = exporter
            exporter.start(edited, destination.absolutePath)
        } catch (e: Exception) {
            exporting = false
            output?.delete()
            error = "Не удалось начать экспорт: ${e.localizedMessage}"
        }
    }
    Dialog(onDismissRequest = ::cancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val dialogView = LocalView.current
        LaunchedEffect(dialogView) {
            (dialogView.parent as? DialogWindowProvider)?.window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        }
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            val footerLimit = maxHeight * .25f
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = ::cancel) { Text("Отмена") }
                    Text("Кадрировать видео", style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    TextButton(enabled = !exporting && clipDuration > 0, onClick = {
                        if (playing) player.pause() else {
                            if (player.currentPosition < (window.start * clipDuration).toLong() || player.currentPosition >= (window.end * clipDuration).toLong()) player.seekTo((window.start * clipDuration).toLong())
                            player.play()
                        }
                    }) { Text(if (playing) "Пауза" else "Просмотр") }
                }
                Text(item.name, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                    val ratio = aspect.takeIf { it.isFinite() && it > 0f } ?: 1f
                    val videoWidth = minOf(maxWidth, maxHeight * ratio)
                    val videoModifier = Modifier.size(videoWidth, videoWidth / ratio)
                    Box(videoModifier) {
                        AndroidView(factory = { ctx ->
                            (android.view.LayoutInflater.from(ctx).inflate(com.mistermikhail.fgallery.R.layout.player_view_preview, null, false) as PlayerView).apply {
                                this.player = player
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setKeepContentOnPlayerReset(true)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        }, modifier = Modifier.fillMaxSize().testTag("video-crop-preview").semantics {
                            stateDescription = "Frame $shownMillis; Ready $previewReady"
                        })
                        CropFrame(crop, !exporting) { crop = it }
                    }
                }
                Column(Modifier.fillMaxWidth().heightIn(max = footerLimit).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(videoTime((clipDuration * window.start).toLong()), style = MaterialTheme.typography.labelMedium)
                    Text(videoTime(shownMillis), style = MaterialTheme.typography.labelMedium)
                    Text(videoTime((clipDuration * window.end).toLong()), style = MaterialTheme.typography.labelMedium)
                }
                TrimTimeline(window, clipDuration, frames, shownMillis, !exporting, ::seekBoundary)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { player.pause(); export(false) }, enabled = !exporting && clipDuration > 0, modifier = Modifier.weight(1f).testTag("video-save-copy")) { Text("Сохранить копию", maxLines = 2) }
                    OutlinedButton(onClick = { player.pause(); export(true) }, enabled = !exporting && clipDuration > 0, modifier = Modifier.weight(1f).testTag("video-save-original")) { Text("Заменить оригинал", maxLines = 2) }
                }
            }
            }
        }
    }
}

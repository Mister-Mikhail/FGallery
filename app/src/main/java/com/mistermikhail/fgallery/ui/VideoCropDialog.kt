package com.mistermikhail.fgallery.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Crop
import androidx.media3.transformer.*
import androidx.media3.transformer.Composition
import coil3.compose.AsyncImage
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.ThumbnailCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoCropDialog(item: MediaItem, onDismiss: () -> Unit, onExported: (Uri, Boolean) -> Unit) {
    val context = LocalContext.current
    var time by remember { mutableStateOf(0f..1f) }
    var horizontal by remember { mutableStateOf(0f..1f) }
    var vertical by remember { mutableStateOf(0f..1f) }
    var exporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var transformer by remember { mutableStateOf<Transformer?>(null) }
    var output by remember { mutableStateOf<File?>(null) }
    val thumbnail by produceState<File?>(null, item.uri) { value = ThumbnailCache.ensure(context, item) }
    var duration by remember { mutableLongStateOf(item.durationMillis) }
    LaunchedEffect(item.uri) {
        if (duration <= 0) duration = withContext(Dispatchers.IO) {
            runCatching {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, item.uri)
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally { retriever.release() }
            }.getOrDefault(0L)
        }
    }
    DisposableEffect(Unit) {
        onDispose { transformer?.cancel(); output?.delete() }
    }
    fun cancel() { transformer?.cancel(); output?.delete(); exporting = false; onDismiss() }
    fun export(replace: Boolean) {
        if (duration <= 0) { error = "Не удалось определить длительность видео"; return }
        exporting = true
        error = null
        try {
            val destination = File.createTempFile("video_crop_", ".mp4", context.cacheDir).also { it.delete() }
            output = destination
            val media = PlayerMediaItem.Builder().setUri(item.uri).setClippingConfiguration(
                PlayerMediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs((duration * time.start).toLong())
                    .setEndPositionMs((duration * time.endInclusive).toLong())
                    .build()
            ).build()
            val effects = Effects(emptyList(), listOf(Crop(
                horizontal.start * 2f - 1f, horizontal.endInclusive * 2f - 1f,
                1f - vertical.endInclusive * 2f, 1f - vertical.start * 2f,
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
    Dialog(onDismissRequest = ::cancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("Кадрировать видео", style = MaterialTheme.typography.titleLarge)
                Text(item.name, maxLines = 1)
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).aspectRatio(item.aspectRatio)) {
                    AsyncImage(thumbnail, "Область кадрирования", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    Canvas(Modifier.fillMaxSize()) {
                        val left = size.width * horizontal.start
                        val top = size.height * vertical.start
                        val width = size.width * (horizontal.endInclusive - horizontal.start)
                        val height = size.height * (vertical.endInclusive - vertical.start)
                        drawRect(Color.White, Offset(left, top), Size(width, height), style = Stroke(3.dp.toPx()))
                    }
                }
                Text("Начало / конец: %.1f — %.1f сек".format(duration * time.start / 1000, duration * time.endInclusive / 1000))
                RangeSlider(time, onValueChange = { if (it.endInclusive - it.start >= 0.01f) time = it }, enabled = !exporting)
                Text("Левая / правая граница кадра")
                RangeSlider(horizontal, onValueChange = { if (it.endInclusive - it.start >= 0.05f) horizontal = it }, enabled = !exporting)
                Text("Верхняя / нижняя граница кадра")
                RangeSlider(vertical, onValueChange = { if (it.endInclusive - it.start >= 0.05f) vertical = it }, enabled = !exporting)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (exporting) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Идёт экспорт. Оригинал остаётся без изменений до успешного завершения.")
                }
                Button(onClick = { export(false) }, enabled = !exporting, modifier = Modifier.fillMaxWidth()) { Text("Сохранить копию") }
                OutlinedButton(onClick = { export(true) }, enabled = !exporting, modifier = Modifier.fillMaxWidth()) { Text("Заменить оригинал…") }
                TextButton(onClick = ::cancel, modifier = Modifier.fillMaxWidth()) { Text(if (exporting) "Отменить экспорт" else "Отмена") }
            }
        }
    }
}

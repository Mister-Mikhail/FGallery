package com.mistermikhail.fgallery.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mistermikhail.fgallery.MainActivity
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.ModelFormat
import com.mistermikhail.fgallery.data.models.ModelPreviewCache
import java.io.File
import java.nio.ByteBuffer
import java.text.NumberFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun ModelViewer(
    item: MediaItem,
    toggleChrome: () -> Unit,
    previous: (() -> Unit)? = null,
    next: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val format = ModelFormat.from(item.name, item.mimeType) ?: return
    var attempt by remember(item) { mutableIntStateOf(0) }
    var model by remember(item, attempt) { mutableStateOf<ByteBuffer?>(null) }
    var bounds by remember(item, attempt) { mutableStateOf<ModelDimensions?>(null) }
    var error by remember(item, attempt) { mutableStateOf<String?>(null) }
    var unit by rememberSaveable(item.uriKey) { mutableStateOf(DimensionUnit.METERS) }

    LaunchedEffect(item, attempt, error) {
        if (error != null || model != null) return@LaunchedEffect
        try {
            model = ModelPreviewCache.load(context.applicationContext, item, format)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: SecurityException) {
            error = "Нет доступа к файлу модели"
        } catch (failure: IllegalArgumentException) {
            error = failure.message?.take(140) ?: "Некорректная модель"
        } catch (_: Exception) {
            error = "Не удалось прочитать модель"
        }
    }
    LaunchedEffect(item, attempt, bounds) {
        if (bounds == null) {
            delay(25_000)
            if (error == null) error = "Подготовка модели заняла слишком много времени"
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xff15171c))) {
        val data = model
        val failure = error
        if (failure != null) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(failure, color = Color.White, modifier = Modifier.testTag("model-error"))
                TextButton(onClick = { attempt++ }) { Text("Повторить") }
                TextButton(onClick = { openModelElsewhere(context, item, format) }) {
                    Text("Открыть в другом приложении")
                }
            }
        } else if (data != null) {
            var surface by remember(data) { mutableStateOf<ModelSurface?>(null) }
            AndroidView(
                factory = { viewContext ->
                    ModelSurface(viewContext, data, { bounds = it }) {
                        error = "Не удалось отобразить модель"
                    }.also { surface = it }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = ModelSurface::release,
                update = {},
            )
            val rendered = surface
            if (rendered != null) {
                LifecycleResumeEffect(rendered) {
                    rendered.setRenderingActive(true)
                    onPauseOrDispose { rendered.setRenderingActive(false) }
                }
                Box(Modifier.fillMaxSize().pointerInput(rendered) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        var motion = Offset.Zero
                        var dragged = false
                        var multitouch = false
                        var event: PointerEvent
                        do {
                            event = awaitPointerEvent()
                            val pointers = event.changes.count { it.pressed }
                            if (pointers > 1) multitouch = true
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            motion += pan
                            if (multitouch || motion.getDistance() > viewConfiguration.touchSlop) dragged = true
                            if (dragged && pointers > 0) {
                                rendered.changeCamera(pan.x, pan.y, zoom, pointers > 1)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (!dragged && event.changes.any { it.id == first.id && !it.isConsumed }) toggleChrome()
                    }
                })
            }
            bounds?.let { dimensions ->
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.78f)).navigationBarsPadding().padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White,
                        modifier = Modifier.padding(top = 8.dp))
                    Text(formatDimensions(dimensions, unit.takeIf { format == ModelFormat.GLB }),
                        color = Color.White, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("model-ready"))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        previous?.let {
                            IconButton(onClick = it) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Предыдущий файл", tint = Color.White)
                            }
                        }
                        TextButton(onClick = { rendered?.fit(resetAngles = false) }) { Text("Вписать") }
                        TextButton(onClick = { rendered?.fit(resetAngles = true) }) { Text("Сбросить") }
                        if (format == ModelFormat.GLB) {
                            TextButton(onClick = { unit = DimensionUnit.entries[(unit.ordinal + 1) % DimensionUnit.entries.size] }) {
                                Text(unit.label)
                            }
                        }
                        next?.let {
                            IconButton(onClick = it) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Следующий файл", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
        if (bounds == null && failure == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (failure != null) {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                previous?.let { TextButton(onClick = it) { Text("Предыдущий файл") } }
                next?.let { TextButton(onClick = it) { Text("Следующий файл") } }
            }
        }
    }
}

private enum class DimensionUnit(val label: String, val multiplier: Double) {
    METERS("м", 1.0),
    CENTIMETERS("см", 100.0),
    MILLIMETERS("мм", 1000.0),
}

private fun formatDimensions(value: ModelDimensions, unit: DimensionUnit?): String {
    val formatter = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 4 }
    val multiplier = unit?.multiplier ?: 1.0
    val dimensions = listOf(value.width, value.height, value.depth).joinToString(" × ") {
        val scaled = it * multiplier
        if (scaled in 0.0..<0.00005 && scaled > 0) "%1.3g".format(scaled)
        else formatter.format(scaled)
    }
    return "$dimensions ${unit?.label ?: "ед. модели"}"
}

private fun openModelElsewhere(context: Context, item: MediaItem, format: ModelFormat) {
    runCatching {
        val uri = if (item.uri.scheme == "file") {
            FileProvider.getUriForFile(context, "${context.packageName}.files", File(requireNotNull(item.uri.path)))
        } else item.uri
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, format.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(view, "Открыть в другом приложении")
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
        context.startActivity(chooser)
    }.onFailure {
        Toast.makeText(context, "Не удалось открыть файл в другом приложении", Toast.LENGTH_SHORT).show()
    }
}

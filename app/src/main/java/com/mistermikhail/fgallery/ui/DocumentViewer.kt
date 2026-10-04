package com.mistermikhail.fgallery.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.PdfSession
import com.mistermikhail.fgallery.data.VisualDocuments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable

@Composable
internal fun PdfViewer(item: MediaItem, onTap: () -> Unit) {
    val context = LocalContext.current
    var document by remember(item.uri) { mutableStateOf<PdfSession?>(null) }
    var error by remember(item.uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.uri) {
        var session: PdfSession? = null
        try {
            // Assign inside NonCancellable so a cancelled load cannot leak its descriptor.
            withContext(Dispatchers.IO + NonCancellable) { session = PdfSession(context, item.uri) }
            document = session
            kotlinx.coroutines.awaitCancellation()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            error = "Не удалось открыть PDF. Проверьте доступ к файлу; защищённый PDF нужно открыть в другом приложении."
        } finally {
            withContext(Dispatchers.IO + NonCancellable) { session?.close() }
        }
    }
    val pdf = document
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            error != null -> Text(error!!, color = Color.White, modifier = Modifier.padding(24.dp))
            pdf == null -> CircularProgressIndicator()
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 64.dp)) {
                items((0 until pdf.pageCount).toList(), key = { it }) { index ->
                    var failed by remember { mutableStateOf(false) }
                    val bitmap by produceState<Bitmap?>(null, item.uri, index) {
                        value = withContext(Dispatchers.IO) { runCatching { pdf.render(index, 1800) }.getOrNull() }
                        failed = value == null
                    }
                    Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${index + 1} / ${pdf.pageCount}", color = Color.LightGray)
                        val page = bitmap
                        if (page != null) {
                            ZoomableDocumentPage(Modifier.fillMaxWidth().aspectRatio(page.width.toFloat() / page.height), onTap) { modifier ->
                                Image(page.asImageBitmap(), "Страница ${index + 1}", contentScale = ContentScale.Fit, modifier = modifier)
                            }
                        } else if (failed) Text("Не удалось загрузить страницу", color = Color.White)
                        else Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SvgViewer(item: MediaItem, onTap: () -> Unit) {
    val context = LocalContext.current
    var error by remember(item.uri) { mutableStateOf(false) }
    val svg by produceState<SVG?>(null, item.uri) {
        value = withContext(Dispatchers.IO) { runCatching { VisualDocuments.loadSvg(context, item.uri) }.getOrNull() }
        error = value == null
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val image = svg
        if (image != null) {
            val ratio = image.documentAspectRatio.takeIf { it > 0f && it.isFinite() } ?: 1f
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val imageModifier = if (maxWidth / maxHeight > ratio) Modifier.height(maxHeight).aspectRatio(ratio) else Modifier.width(maxWidth).aspectRatio(ratio)
                ZoomableDocumentPage(imageModifier, onTap) { modifier ->
                    Canvas(modifier.background(Color(0xFF303030))) {
                        drawIntoCanvas { canvas -> image.renderToCanvas(canvas.nativeCanvas, RectF(0f, 0f, size.width, size.height)) }
                    }
                }
            }
        } else if (error) Text("Не удалось открыть SVG: файл повреждён или недоступен", color = Color.White, modifier = Modifier.padding(24.dp))
        else CircularProgressIndicator()
    }
}

@Composable
private fun ZoomableDocumentPage(modifier: Modifier, onTap: () -> Unit, content: @Composable (Modifier) -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var stage by remember { mutableIntStateOf(0) }
    Box(modifier, contentAlignment = Alignment.Center) {
        content(Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() }, onDoubleTap = {
                    zoom = when (stage) { 0 -> 1.3f; 1 -> 8f; else -> 1f }
                    stage = (stage + 1) % 3
                    pan = Offset.Zero
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, delta, factor, _ ->
                    zoom = (zoom * factor).coerceIn(1f, 8f)
                    val maxX = size.width * (zoom - 1) / 2
                    val maxY = size.height * (zoom - 1) / 2
                    pan = Offset((pan.x + delta.x).coerceIn(-maxX, maxX), (pan.y + delta.y).coerceIn(-maxY, maxY))
                }
            }
            .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y })
    }
}

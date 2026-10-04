package com.mistermikhail.fgallery.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
        } catch (e: Exception) {
            error = when {
                e is SecurityException -> "Нет доступа к PDF. Выберите файл повторно или разрешите доступ к накопителю."
                e.javaClass.simpleName.contains("Password", true) -> "Для открытия PDF нужен пароль."
                else -> "Не удалось прочитать PDF. ${e.localizedMessage.orEmpty()}"
            }
        } finally {
            withContext(Dispatchers.IO + NonCancellable) { session?.close() }
        }
    }
    val pdf = document
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            error != null -> Text(error!!, color = Color.White, modifier = Modifier.padding(24.dp))
            pdf == null -> CircularProgressIndicator()
            else -> LazyColumn(Modifier.fillMaxSize().testTag("pdf-pages"), contentPadding = PaddingValues(vertical = 64.dp)) {
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
                            ZoomableDocumentPage(Modifier.fillMaxWidth().aspectRatio(page.width.toFloat() / page.height).testTag("pdf-page-$index"), onTap) { modifier ->
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
        if (image != null && !error) {
            val ratio = image.documentAspectRatio.takeIf { it > 0f && it.isFinite() } ?: 1f
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val imageModifier = if (maxWidth / maxHeight > ratio) Modifier.height(maxHeight).aspectRatio(ratio) else Modifier.width(maxWidth).aspectRatio(ratio)
                ZoomableDocumentPage(imageModifier.testTag("svg-page"), onTap) { modifier ->
                    Canvas(modifier.background(Color(0xFF303030))) {
                        drawIntoCanvas { canvas ->
                            runCatching { image.renderToCanvas(canvas.nativeCanvas, RectF(0f, 0f, size.width, size.height)) }.onFailure { error = true }
                        }
                    }
                }
            }
        } else if (error) Text("Не удалось открыть SVG: файл повреждён или недоступен", color = Color.White, modifier = Modifier.padding(24.dp))
        else CircularProgressIndicator()
    }
}

@Composable
internal fun ZoomableDocumentPage(modifier: Modifier, onTap: () -> Unit, content: @Composable (Modifier) -> Unit) {
    var transform by remember { mutableStateOf(DocumentTransform()) }
    var bounds by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val scope = rememberCoroutineScope()
    var animation by remember { mutableStateOf<Job?>(null) }
    val scrollDispatcher = remember { NestedScrollDispatcher() }
    val connection = remember { object : NestedScrollConnection {} }
    val latestTap by rememberUpdatedState(onTap)
    Box(modifier
        .clipToBounds()
        .onSizeChanged { bounds = it }
        .nestedScroll(connection, scrollDispatcher)
        .semantics { stateDescription = "Zoom ${(transform.scale * 100).toInt()}%" }
        .pointerInput(Unit) {
            detectTapGestures(onTap = { latestTap() }, onDoubleTap = { focus ->
                animation?.cancel()
                if (!focus.x.isFinite() || !focus.y.isFinite() || bounds.width <= 0 || bounds.height <= 0) return@detectTapGestures
                val target = nextDoubleTapZoom(transform.scale, 1f, 8f)
                val start = transform
                animation = scope.launch {
                    animate(start.scale, target, animationSpec = tween(240)) { scale, _ ->
                        transform = transformDocument(start, focus.x, focus.y, 0f, 0f, scale, bounds.width.toFloat(), bounds.height.toFloat())
                    }
                }
            })
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var pastSlop = false
                var accumulatedPan = Offset.Zero
                var accumulatedZoom = 1f
                do {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.changes.any { it.isConsumed }) break
                    val pointers = event.changes.count { it.pressed }
                    if (pointers == 0) break // A release event has no centroid; never transform it.
                    val pan = event.calculatePan()
                    val zoom = event.calculateZoom()
                    // A normal one-finger drag belongs to the PDF LazyColumn.
                    if (pointers >= 2 || transform.scale > 1.001f) {
                        accumulatedPan += pan
                        accumulatedZoom *= zoom
                        if (!pastSlop) {
                            pastSlop = accumulatedPan.getDistance() > viewConfiguration.touchSlop ||
                                kotlin.math.abs(1f - accumulatedZoom) * minOf(size.width, size.height) > viewConfiguration.touchSlop
                        }
                        if (pastSlop) {
                            animation?.cancel()
                            val focus = event.calculateCentroid(useCurrent = false)
                            if (!focus.x.isFinite() || !focus.y.isFinite() || !zoom.isFinite() || zoom <= 0f) continue
                            val before = transform
                            transform = transformDocument(before, focus.x, focus.y, pan.x, pan.y,
                                before.scale * zoom, size.width.toFloat(), size.height.toFloat())
                            // Once pan reaches an edge, forward the remaining drag to the page list.
                            if (pointers == 1 && zoom == 1f) {
                                val consumed = Offset(transform.x - before.x, transform.y - before.y)
                                val remaining = pan - consumed
                                if (remaining.getDistance() > 0f) scrollDispatcher.dispatchPostScroll(consumed, remaining, NestedScrollSource.UserInput)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }, contentAlignment = Alignment.Center) {
        // Gesture coordinates stay in the fixed outer Box; only the drawing is transformed.
        content(Modifier.fillMaxSize().graphicsLayer {
            scaleX = transform.scale; scaleY = transform.scale
            translationX = transform.x; translationY = transform.y
        })
    }
}

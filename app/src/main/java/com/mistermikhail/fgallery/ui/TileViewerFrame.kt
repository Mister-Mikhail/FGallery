package com.mistermikhail.fgallery.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.mistermikhail.fgallery.data.MediaItem

internal class ViewerTiles {
    data class Tile(val coordinates: LayoutCoordinates, val item: MediaItem, val fit: Boolean)
    val visible = mutableMapOf<String, Tile>()
    var followUri by mutableStateOf<String?>(null)
}
internal val LocalViewerTiles = staticCompositionLocalOf { ViewerTiles() }

@Composable
internal fun Modifier.viewerTile(item: MediaItem, fit: Boolean = false): Modifier {
    val tiles = LocalViewerTiles.current
    val uri = item.uriKey
    DisposableEffect(tiles, uri) { onDispose { tiles.visible.remove(uri) } }
    return onGloballyPositioned { tiles.visible[uri] = ViewerTiles.Tile(it, item, fit) }
}

/** A single uniform scale preserves the image ratio. The clip uncrops a square or mosaic tile. */
@Composable
internal fun TileViewerFrame(item: MediaItem?, content: @Composable BoxScope.(MediaItem) -> Unit) {
    val tiles = LocalViewerTiles.current
    var retained by remember { mutableStateOf<MediaItem?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(item) {
        if (item != null) {
            retained = item
            tiles.followUri = item.uriKey
            progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        } else if (retained != null) {
            progress.animateTo(0f, tween(340, easing = FastOutSlowInEasing))
            retained = null
            tiles.followUri = null
        }
    }
    val displayed = item ?: retained ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.drawWithContent {
        val t = progress.value
        val uri = tiles.followUri ?: displayed.uriKey
        val source = tiles.visible[uri]
        val coords = source?.coordinates?.takeIf { it.isAttached && t < 1f }
        val tile = coords?.boundsInRoot()?.translate(-origin)?.takeUnless { it.isEmpty }
            ?: Rect(size.width * .4f, size.height * .4f, size.width * .6f, size.height * .6f)
        if (t >= 1f || size.width <= 0f || size.height <= 0f) {
            drawContent()
        } else {
            val w = size.width
            val h = size.height
            val startScale = viewerTileScale(w, h, tile.width, tile.height, source?.item?.aspectRatio ?: displayed.aspectRatio, source?.fit == true)
            val scale = startScale + (1f - startScale) * t
            val k = 1f - t
            clipRect(tile.left * k, tile.top * k, tile.right * k + w * t, tile.bottom * k + h * t) {
                withTransform({
                    translate((tile.center.x - w / 2) * k, (tile.center.y - h / 2) * k)
                    scale(scale, scale, Offset(w / 2, h / 2))
                }) { this@drawWithContent.drawContent() }
            }
        }
    }) {
        Box(Modifier.fillMaxSize().graphicsLayer()) { content(displayed) }
        // Use the already cached tile during the handoff to the full decoder.
        val showThumbnail by remember { derivedStateOf { progress.value < .70f } }
        if (showThumbnail) {
            val uri = tiles.followUri ?: displayed.uriKey
            val source = tiles.visible[uri]
            if (source != null && source.coordinates.isAttached) {
                val photo = source.item
                val context = androidx.compose.ui.platform.LocalContext.current
                val model = remember(photo, source.coordinates.size) {
                    val bounds = source.coordinates.size
                    val high = maxOf(bounds.width, bounds.height) > 480
                    coil3.request.ImageRequest.Builder(context)
                        .data(com.mistermikhail.fgallery.data.GalleryThumb(photo, high))
                        .memoryCacheKey("gallery/v4/${com.mistermikhail.fgallery.data.ThumbnailCache.key(photo)}/${if (high) "hq" else "micro"}")
                        .size(bounds.width.coerceAtLeast(1), bounds.height.coerceAtLeast(1)).build()
                }
                coil3.compose.AsyncImage(model, null, modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = (1f - progress.value / .70f).coerceIn(0f, 1f)
                }, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
        }
    }
}

internal fun viewerTileScale(width: Float, height: Float, tileWidth: Float, tileHeight: Float, photoRatio: Float, fit: Boolean = false): Float {
    val fittedWidth = minOf(width, height * photoRatio)
    val x = tileWidth / fittedWidth
    val y = tileHeight / (fittedWidth / photoRatio)
    return if (fit) minOf(x, y) else maxOf(x, y)
}

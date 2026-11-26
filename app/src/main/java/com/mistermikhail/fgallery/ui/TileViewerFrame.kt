package com.mistermikhail.fgallery.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
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
    data class Tile(val coordinates: LayoutCoordinates, val aspectRatio: Float)
    val visible = mutableMapOf<String, Tile>()
    var followUri by mutableStateOf<String?>(null)
}
internal val LocalViewerTiles = staticCompositionLocalOf { ViewerTiles() }

@Composable
internal fun Modifier.viewerTile(item: MediaItem): Modifier {
    val tiles = LocalViewerTiles.current
    val uri = item.uriKey
    DisposableEffect(tiles, uri) { onDispose { tiles.visible.remove(uri) } }
    return onGloballyPositioned { tiles.visible[uri] = ViewerTiles.Tile(it, item.aspectRatio) }
}

/** A single uniform scale preserves the image ratio. The clip uncrops a square or mosaic tile. */
@Composable
internal fun TileViewerFrame(transition: Transition<EnterExitState>, content: @Composable () -> Unit) {
    val tiles = LocalViewerTiles.current
    val progress = transition.animateFloat(transitionSpec = { tween(250) }, label = "tile reveal") {
        if (it == EnterExitState.Visible) 1f else 0f
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.drawWithContent {
        val t = progress.value
        val uri = tiles.followUri
        val source = tiles.visible[uri]
        val coords = source?.coordinates?.takeIf { it.isAttached && t < 1f }
        val tile = coords?.boundsInRoot()?.translate(-origin)
        if (source == null || tile == null || tile.isEmpty || size.width <= 0f || size.height <= 0f) {
            drawContent()
        } else {
            val w = size.width
            val h = size.height
            val startScale = viewerTileScale(w, h, tile.width, tile.height, source.aspectRatio)
            val scale = startScale + (1f - startScale) * t
            val k = 1f - t
            clipRect(tile.left * k, tile.top * k, tile.right * k + w * t, tile.bottom * k + h * t) {
                withTransform({
                    translate((tile.center.x - w / 2) * k, (tile.center.y - h / 2) * k)
                    scale(scale, scale, Offset(w / 2, h / 2))
                }) { this@drawWithContent.drawContent() }
            }
        }
    }) { content() }
}

internal fun viewerTileScale(width: Float, height: Float, tileWidth: Float, tileHeight: Float, photoRatio: Float): Float {
    val fittedWidth = minOf(width, height * photoRatio)
    return maxOf(tileWidth / fittedWidth, tileHeight / (fittedWidth / photoRatio))
}

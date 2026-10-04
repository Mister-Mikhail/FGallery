package com.mistermikhail.fgallery

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.ThumbnailCache
import com.mistermikhail.fgallery.ui.TiledZoomableImage
import com.mistermikhail.fgallery.ui.ViewerImageReady
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ImageZoomTest {
    @get:Rule val rule = createComposeRule()

    @Test fun transparentPngZoomsThirtyPercentWithoutStaticDuplicate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "transparent-zoom.png")
        val bitmap = Bitmap.createBitmap(4000, 4000, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawRect(0f, 0f, 1000f, 1000f, android.graphics.Paint().apply { color = android.graphics.Color.RED })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val item = MediaItem(987654, Uri.fromFile(file), file.name, "image/png", MediaKind.IMAGE, 0, 4000, 4000, 0, "Tests", "", file.length())
        val cache = ThumbnailCache.fileFor(context, item, highQuality = true)
        cache.parentFile?.mkdirs()
        file.copyTo(cache, overwrite = true)
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                TiledZoomableImage(item, {}, false, {})
            }
        }
        val node = rule.onNodeWithTag("viewer-image")
        // Real decode runs outside Compose's animation clock.
        rule.waitUntil(15000) {
            runCatching {
                val pixels = node.captureToImage().toPixelMap()
                val side = minOf(pixels.width, pixels.height)
                pixels[(pixels.width - side) / 2 + side / 10, (pixels.height - side) / 2 + side / 10].red > .8f
            }.getOrDefault(false)
        }
        rule.waitUntil(15000) { runCatching { node.fetchSemanticsNode().config[ViewerImageReady] }.getOrDefault(false) }
        node.performTouchInput { doubleClick(Offset(width * .8f, height * .7f)) }
        rule.waitForIdle()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 130%"))
        node.performTouchInput { doubleClick(Offset(width * .8f, height * .7f)) }
        rule.waitForIdle()
        val pixels = node.captureToImage().toPixelMap()
        val side = minOf(pixels.width, pixels.height)
        val oldPreviewPixel = pixels[(pixels.width - side) / 2 + side / 10, (pixels.height - side) / 2 + side / 10]
        assertTrue("Static red preview must disappear after zoom", oldPreviewPixel.red < .1f)
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
    }
    @Test fun cameraJpegZoomChangesPixelsAndRestartsAfterPinchToFit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "camera-zoom.jpg")
        val bitmap = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawRect(1500f, 1000f, 2500f, 2000f, android.graphics.Paint().apply { color = android.graphics.Color.RED })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        androidx.exifinterface.media.ExifInterface(file).apply {
            setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, "6")
            saveAttributes()
        }
        val item = MediaItem(987655, Uri.fromFile(file), file.name, "image/jpeg", MediaKind.IMAGE, 0, 3000, 4000, 0, "Tests", "", file.length())
        rule.setContent { Box(Modifier.fillMaxSize().background(Color.Black)) { TiledZoomableImage(item, {}, false, {}) } }
        val node = rule.onNodeWithTag("viewer-image")
        rule.waitUntil(15000) { runCatching { node.fetchSemanticsNode().config[ViewerImageReady] }.getOrDefault(false) }
        fun redWidth(): Int {
            val pixels = node.captureToImage().toPixelMap()
            return (0 until pixels.width).count { x -> val c = pixels[x, pixels.height / 2]; c.red > .8f && c.green < .2f }
        }
        val before = redWidth()
        assertTrue(before > 20)
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        val ratio = redWidth().toFloat() / before
        assertTrue("First zoom must change visible pixels by 30%, got $ratio", ratio in 1.25f..1.35f)
        node.performTouchInput {
            pinch(start0 = center - Offset(100f, 0f), end0 = center - Offset(10f, 0f), start1 = center + Offset(100f, 0f), end1 = center + Offset(10f, 0f), durationMillis = 600)
        }
        rule.waitForIdle()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 130%"))
    }

}

package com.mistermikhail.fgallery

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.*
import com.mistermikhail.fgallery.ui.TiledZoomableImage
import com.mistermikhail.fgallery.ui.ViewerImageReady
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TiffImageTest {
    @get:Rule val rule = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun fixture(name: String) = File(context.cacheDir, name).apply {
        instrumentation.context.assets.open(name).use { input -> outputStream().use { input.copyTo(it) } }
    }

    @Test fun decodeRealTiffCompressionAndOrientation() {
        for (name in listOf("raw.tif", "tiff_lzw.tif", "tiff_adobe_deflate.tif", "jpeg.tif")) {
            val bitmap = TiffImages.decode(fixture(name))
            assertEquals(if (name == "raw.tif") 32 else 320, bitmap.width)
            assertEquals(if (name == "raw.tif") 24 else 240, bitmap.height)
            val red = bitmap.getPixel(bitmap.width / 8, bitmap.height / 2); val green = bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height / 2)
            assertTrue(name, android.graphics.Color.red(red) > 220 && android.graphics.Color.green(red) < 30)
            assertTrue(name, android.graphics.Color.green(green) > 220 && android.graphics.Color.red(green) < 30)
            bitmap.recycle()
        }
        val rotated = TiffImages.decode(fixture("oriented.tif"))
        assertEquals(240, rotated.width); assertEquals(320, rotated.height)
        rotated.recycle()
        val gray = TiffImages.decode(fixture("gray16.tif"))
        assertEquals(320, gray.width); assertEquals(240, gray.height)
        gray.recycle()
    }

    @Test fun tiffThumbnailAndViewerSupportTheSameZoomCycle() {
        val file = fixture("tiff_lzw.tif")
        val item = MediaItem(7801, Uri.fromFile(file), file.name, "image/tiff", MediaKind.IMAGE, 0, 320, 240, 0, "Tests", "", file.length())
        val thumbnail = runBlocking { ThumbnailCache.ensure(context, item) }
        assertNotNull(thumbnail)
        rule.setContent { Box(Modifier.fillMaxSize()) { TiledZoomableImage(item, {}, false, {}) } }
        val node = rule.onNodeWithTag("viewer-image")
        rule.waitUntil(15000) { runCatching { node.fetchSemanticsNode().config[ViewerImageReady] }.getOrDefault(false) }
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 310%"))
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        val maximum = node.fetchSemanticsNode().config[SemanticsProperties.StateDescription].removePrefix("Zoom ").removeSuffix("%").toInt()
        assertTrue(maximum >= 700)
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
    }
}

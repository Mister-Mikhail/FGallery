package com.mistermikhail.fgallery

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.ThumbnailCache
import com.mistermikhail.fgallery.ui.TiledZoomableImage
import com.mistermikhail.fgallery.ui.REMEMBER_ROTATION_KEY
import com.mistermikhail.fgallery.ui.ViewerImageReady
import com.mistermikhail.fgallery.ui.ViewerZoomRange
import com.mistermikhail.fgallery.ui.nextRasterDoubleTapZoom
import com.mistermikhail.fgallery.ui.imageOrientationPreferences
import com.mistermikhail.fgallery.ui.imageQuarterTurns
import com.mistermikhail.fgallery.ui.saveImageQuarterTurns
import com.mistermikhail.fgallery.ui.viewerGesturePreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ImageZoomTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rotatingPortraitRefitsTheViewerAndRemembersOnlyWhenEnabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("rotation-fit-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(480, 720, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.GREEN)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        val originalBytes = file.readBytes()
        val item = MediaItem(987659, Uri.fromFile(file), file.name, "image/png", MediaKind.IMAGE,
            0, 480, 720, 0, "Tests", "", file.length(), dateModifiedMillis = file.lastModified())
        val preferences = viewerGesturePreferences(context)
        val orientations = imageOrientationPreferences(context)
        val previousSetting = preferences.all[REMEMBER_ROTATION_KEY] as? Boolean
        val session = mutableIntStateOf(0)
        try {
            preferences.edit().putBoolean(REMEMBER_ROTATION_KEY, false).apply()
            rule.setContent {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.size(240.dp, 320.dp).background(Color.Black).testTag("rotation-test-viewport")) {
                        key(session.intValue) { TiledZoomableImage(item, {}, false, {}) }
                    }
                }
            }
            val viewport = rule.onNodeWithTag("rotation-test-viewport")
            val image = rule.onNodeWithTag("viewer-image")
            fun awaitOrientation(landscape: Boolean) {
                rule.waitUntil(8_000) {
                    runCatching {
                        if (!image.fetchSemanticsNode().config[ViewerImageReady]) return@runCatching false
                        val pixels = viewport.captureToImage().toPixelMap()
                        fun greenAt(x: Float, y: Float) = pixels[(pixels.width * x).toInt(), (pixels.height * y).toInt()].green > .8f
                        greenAt(.5f, .5f) && greenAt(.025f, .5f) == landscape &&
                            greenAt(.975f, .5f) == landscape && greenAt(.5f, .22f) != landscape
                    }.getOrDefault(false)
                }
            }
            fun turnAndStretch() {
                viewport.performTouchInput {
                    val startRadius = width * .19f
                    down(0, center - Offset(startRadius, 0f))
                    down(1, center + Offset(startRadius, 0f))
                    for (step in 1..6) {
                        val angle = step * Math.PI / 12
                        val radius = startRadius * (1 + step * .07f)
                        val offset = Offset((radius * kotlin.math.cos(angle)).toFloat(), (radius * kotlin.math.sin(angle)).toFloat())
                        updatePointerTo(0, center - offset)
                        updatePointerTo(1, center + offset)
                        move()
                    }
                    up(1)
                    up(0)
                }
                awaitOrientation(landscape = true)
                image.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
            }
            fun reopen() = rule.runOnIdle { session.intValue++ }

            awaitOrientation(landscape = false)
            turnAndStretch()
            assertEquals(0, orientations.imageQuarterTurns(item))
            reopen()
            awaitOrientation(landscape = false)

            preferences.edit().putBoolean(REMEMBER_ROTATION_KEY, true).apply()
            turnAndStretch()
            assertEquals(1, orientations.imageQuarterTurns(item))
            reopen()
            awaitOrientation(landscape = true)
            assertArrayEquals(originalBytes, file.readBytes())

            preferences.edit().putBoolean(REMEMBER_ROTATION_KEY, false).apply()
            reopen()
            awaitOrientation(landscape = false)
        } finally {
            orientations.saveImageQuarterTurns(item, 0)
            val restore = preferences.edit()
            if (previousSetting == null) restore.remove(REMEMBER_ROTATION_KEY)
            else restore.putBoolean(REMEMBER_ROTATION_KEY, previousSetting)
            restore.apply()
            file.delete()
        }
    }

    @Test fun smallImageZoomsTwoAndHalfThenReturnsOnSecondTap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "small-zoom.png")
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val item = MediaItem(987650, Uri.fromFile(file), file.name, "image/png", MediaKind.IMAGE, 0, 64, 48, 0, "Tests", "", file.length())
        rule.setContent { Box(Modifier.fillMaxSize().background(Color.Black)) { TiledZoomableImage(item, {}, false, {}) } }
        val node = rule.onNodeWithTag("viewer-image")
        rule.waitUntil(15000) { runCatching { node.fetchSemanticsNode().config[ViewerImageReady] }.getOrDefault(false) }
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 250%"))
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
    }

    @Test fun transparentPngDoublesWithoutStaticDuplicate() {
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
        run {
            val (fit, maximum) = node.fetchSemanticsNode().config[ViewerZoomRange]
            val actual = node.fetchSemanticsNode().config[SemanticsProperties.StateDescription].removePrefix("Zoom ").removeSuffix("%").toInt()
            assertEquals("Tap and pinch must share their ceiling", maxOf(8f, fit * 8f), maximum, .001f)
            val expected = nextRasterDoubleTapZoom(fit, fit, maximum) / fit * 100f
            assertTrue("Absolute first raster zoom: $actual vs $expected", kotlin.math.abs(actual - expected) < 2f)
        }
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
        canvas.drawRect(1900f, 1400f, 2100f, 1600f, android.graphics.Paint().apply { color = android.graphics.Color.RED })
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
        assertTrue(before > 10)
        val (fit, maximum) = node.fetchSemanticsNode().config[ViewerZoomRange]
        val expected = nextRasterDoubleTapZoom(fit, fit, maximum) / fit
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        val ratio = redWidth().toFloat() / before
        assertTrue("First zoom ratio $ratio expected $expected", kotlin.math.abs(ratio - expected) < expected * .12f)
        node.performTouchInput {
            pinch(start0 = center - Offset(100f, 0f), end0 = center - Offset(10f, 0f), start1 = center + Offset(100f, 0f), end1 = center + Offset(10f, 0f), durationMillis = 600)
        }
        rule.waitForIdle()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        run {
            val (fit, maximum) = node.fetchSemanticsNode().config[ViewerZoomRange]
            val actual = node.fetchSemanticsNode().config[SemanticsProperties.StateDescription].removePrefix("Zoom ").removeSuffix("%").toInt()
            val expected = nextRasterDoubleTapZoom(fit, fit, maximum) / fit * 100f
            assertTrue("Absolute first raster zoom: $actual vs $expected", kotlin.math.abs(actual - expected) < 2f)
        }
    }

    @Test fun afterSecondDoubleTapPinchStillReachesUnchangedMaximum() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "pinch-ceiling-zoom.jpg")
        val bitmap = Bitmap.createBitmap(6000, 4000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        bitmap.recycle()
        val item = MediaItem(987658, Uri.fromFile(file), file.name, "image/jpeg",
            MediaKind.IMAGE, 0, 6000, 4000, 0, "Tests", "", file.length())
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                TiledZoomableImage(item, {}, false, {}, zoomSecondPower = .85f)
            }
        }
        val node = rule.onNodeWithTag("viewer-image")
        rule.waitUntil(15000) {
            runCatching { node.fetchSemanticsNode().config[ViewerImageReady] }.getOrDefault(false)
        }
        fun zoom() = node.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            .removePrefix("Zoom ").removeSuffix("%").toInt()
        val (fit, maximum) = node.fetchSemanticsNode().config[ViewerZoomRange]
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        val desired = (nextRasterDoubleTapZoom(fit, fit, maximum) / fit * 100).toInt()
        assertTrue("First tap should target 50% file pixels, got ${zoom()} vs $desired", kotlin.math.abs(desired - zoom()) <= 2)
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        val afterTap = zoom()
        assertTrue("Second tap should leave pinch headroom", afterTap < (maximum / fit * 100).toInt() - 15)
        node.performTouchInput {
            pinch(
                start0 = center - Offset(30f, 0f), end0 = center - Offset(190f, 0f),
                start1 = center + Offset(30f, 0f), end1 = center + Offset(190f, 0f),
                durationMillis = 700,
            )
        }
        rule.waitForIdle()
        assertTrue("Pinch must zoom farther after second tap: ${zoom()} <= $afterTap", zoom() > afterTap + 8)
        assertTrue("Pinch must not cross the configured maximum", zoom() <= (maximum / fit * 100).toInt() + 2)
        file.delete()
    }

}

package com.mistermikhail.fgallery

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.*
import com.mistermikhail.fgallery.ui.VideoCropDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class VideoCropTest {
    @get:Rule val rule = createComposeRule()

    @Test fun cornersTrimPreviewAndExport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "crop-video.mp4")
        instrumentation.context.assets.open(file.name).use { input -> file.outputStream().use { input.copyTo(it) } }
        val item = MediaItem(9001, Uri.fromFile(file), file.name, "video/mp4", MediaKind.VIDEO, 0, 320, 240, 4000, "Tests", "", file.length())
        val result = AtomicReference<Uri>()
        rule.setContent { VideoCropDialog(item, {}, { uri, replace -> assertFalse(replace); result.set(uri) }) }
        val preview = rule.onNodeWithTag("video-crop-preview")
        rule.waitUntil(20000) { runCatching { preview.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Ready true") }.getOrDefault(false) }
        val frame = rule.onNodeWithTag("video-crop-area")
        frame.performTouchInput { swipe(Offset(2f, 2f), Offset(width * .2f, height * .2f), 600) }
        rule.waitForIdle()
        val crop = frame.fetchSemanticsNode().config[SemanticsProperties.StateDescription].removePrefix("Crop ").split(',').map(String::toFloat)
        assertTrue("Top corner must move down", crop[1] > .1f)
        val timeline = rule.onNodeWithTag("video-trim-timeline")
        timeline.performTouchInput { swipe(Offset(14f, height / 2f), Offset(width * .25f, height / 2f), 600) }
        rule.waitForIdle()
        val first = preview.fetchSemanticsNode().config[SemanticsProperties.StateDescription].substringAfter("Frame ").substringBefore(';').toLong()
        assertTrue("Dragging start shows its boundary frame", first in 500..1500)
        timeline.performTouchInput { swipe(Offset(width - 14f, height / 2f), Offset(width * .75f, height / 2f), 600) }
        rule.waitForIdle()
        val last = preview.fetchSemanticsNode().config[SemanticsProperties.StateDescription].substringAfter("Frame ").substringBefore(';').toLong()
        assertTrue("Dragging end shows its boundary frame", last in 2500..3500)
        rule.onNodeWithText("Сохранить копию").performClick()
        rule.waitUntil(90000) { result.get() != null }
        val output = File(result.get().path!!)
        assertTrue(output.length() > 0)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(output.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Saved video has cropped width", width in 200..300)
            assertTrue("Saved video has cropped height", height in 140..220)
            assertTrue("Saved video has trimmed duration", duration in 1500..2500)
            assertNotNull(retriever.getFrameAtTime(500000))
        } finally { retriever.release(); output.delete() }
        assertTrue("Copy leaves source intact", file.exists())
    }
    @Test fun saveButtonsRemainInsideWindowWithLargeText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "crop-video-large.mp4")
        instrumentation.context.assets.open("crop-video.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val item = MediaItem(9002, Uri.fromFile(file), file.name, "video/mp4", MediaKind.VIDEO, 0, 320, 240, 4000, "Tests", "", file.length())
        val fontScale = mutableStateOf(2f)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) { VideoCropDialog(item, {}, { _, _ -> }) }
        }
        val preview = rule.onNodeWithTag("video-crop-preview")
        rule.waitUntil(20000) { runCatching { preview.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("Ready true") }.getOrDefault(false) }
        rule.waitForIdle()
        val display = context.resources.displayMetrics
        for (tag in listOf("video-save-copy", "video-save-original")) {
            val node = rule.onNodeWithTag(tag)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            android.util.Log.i("VideoControls", "$tag bounds=$bounds screen=${display.widthPixels}x${display.heightPixels}")
            try { node.assertIsDisplayed() } catch (error: AssertionError) {
                instrumentation.uiAutomation.takeScreenshot()?.let { image ->
                    val screenshot = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "video-controls-failure.png")
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/FGalleryTests/")
                        })
                    if (screenshot != null) context.contentResolver.openOutputStream(screenshot, "w")!!.use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    image.recycle()
                }
                throw error
            }
            assertTrue("$tag must fit below navigation bar", bounds.bottom <= display.heightPixels)
            assertTrue("$tag must fit horizontally", bounds.right <= display.widthPixels)
        }
        rule.runOnIdle { fontScale.value = 1f }
        rule.onNodeWithTag("video-save-copy").assertIsDisplayed()
    }

}

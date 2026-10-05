package com.mistermikhail.fgallery

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.*
import com.mistermikhail.fgallery.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class RecycleBinViewerTest {
    @get:Rule val rule = createComposeRule()
    @Test fun trashFilesOpenZoomBrowseAndReturnWithoutMovingThemAgain() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "bin-viewer-${UUID.randomUUID()}").apply { mkdirs() }
        val root = StorageRoot("test", "Tests", directory.path, directory)
        val bin = DriveRecycleBin(context, { listOf(root) }, { emptyList() })
        val photo = File(directory, "a.jpg")
        Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.GREEN)
            photo.outputStream().use { compress(Bitmap.CompressFormat.JPEG, 95, it) }; recycle()
        }
        val video = File(directory, "b.mp4")
        instrumentation.context.assets.open("crop-video.mp4").use { source -> video.outputStream().use { source.copyTo(it) } }
        val sources = listOf(photo, video).map { file ->
            DriveLibrary.item(Uri.fromFile(file), file.name, if (file == photo) "image/jpeg" else "video/mp4", file.length(), 0, root, directory.path)!!
        }
        var opened by mutableStateOf<MediaItem?>(null)
        try {
            val moved = runBlocking { bin.trash(sources); bin.load() }.sortedBy { it.name }
            assertEquals(2, moved.size)
            rule.setContent {
                val current = opened
                if (current == null) RecycleBinScreen(moved, emptySet(), {}, {}, {}, {}, {}, {}, { opened = it })
                else ViewerScreen(
                    items = moved, initialItem = current, onBack = { opened = null },
                    onTrash = { error("A trash viewer must never move a file again") },
                    quickExifEnabled = false, cleanupMode = true, recycleBinMode = true,
                    onShare = {}, onCrop = { error("No crop in trash") }, onRename = { _, _ -> error("No rename in trash") }, onMove = {},
                )
            }
            rule.onNodeWithContentDescription("a.jpg").performClick()
            val image = rule.onNodeWithTag("viewer-image")
            rule.waitUntil(15000) { runCatching { image.fetchSemanticsNode().config[ViewerImageReady] }.getOrDefault(false) }
            image.performTouchInput { doubleClick(center) }
            rule.waitForIdle()
            image.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Zoom 200%"))
            image.performTouchInput { doubleClick(center) }
            rule.waitForIdle()
            image.performTouchInput { advanceEventTime(400); click(center) }
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Кадрировать").assertDoesNotExist()
            rule.onNodeWithContentDescription("В корзину").assertDoesNotExist()
            rule.onNodeWithContentDescription("Переименовать").assertDoesNotExist()
            rule.onNodeWithContentDescription("Назад").performClick()
            rule.onNodeWithText("Корзина").assertExists()
            rule.onNodeWithContentDescription("a.jpg").performClick()
            rule.onNodeWithTag("viewer-root").performTouchInput { swipeLeft() }
            rule.waitUntil(20000) {
                rule.onAllNodesWithTag("viewer-video").fetchSemanticsNodes().any { node ->
                    node.config.getOrElse(ViewerCurrentUri) { "" } == moved[1].uriKey && node.config.getOrElse(ViewerVideoReady) { false }
                } && rule.onNodeWithTag("viewer-root").fetchSemanticsNode().config[ViewerCurrentUri] == moved[1].uriKey
            }
            rule.onNodeWithTag("viewer-root").performTouchInput { doubleClick(center) }
            rule.waitForIdle()
            assertEquals(2, runBlocking { bin.load() }.size)
            assertTrue(moved.all { File(it.sourcePath).exists() })
        } finally { directory.deleteRecursively() }
    }
}

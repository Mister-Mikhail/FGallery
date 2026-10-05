package com.mistermikhail.fgallery

import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.*
import com.mistermikhail.fgallery.ui.*
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ViewerDeletionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun deletingCurrentVideoShowsNextThenPreviousWithANewRenderedFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "viewer-trash-${UUID.randomUUID()}").apply { mkdirs() }
        val root = StorageRoot("test", "Tests", directory.path, directory)
        val bin = DriveRecycleBin(context, { listOf(root) }, { emptyList() })
        val items = listOf("a.mp4", "b.mp4", "c.mp4").map { name ->
            val file = File(directory, name)
            instrumentation.context.assets.open("crop-video.mp4").use { source -> file.outputStream().use { source.copyTo(it) } }
            DriveLibrary.item(Uri.fromFile(file), name, "video/mp4", file.length(), 0, root, directory.path)!!
        }
        var shown by mutableStateOf(items)
        var current by mutableStateOf<MediaItem?>(items[1])
        var deleting by mutableStateOf<String?>(null)
        var failure by mutableStateOf<Throwable?>(null)
        try {
            rule.setContent {
                val scope = rememberCoroutineScope()
                val opened = current
                if (opened == null) Text("Папка пуста") else ViewerScreen(
                    items = shown, initialItem = opened, onBack = {}, quickExifEnabled = false, cleanupMode = true,
                    onShare = {}, onCrop = {}, onRename = { _, _ -> }, onMove = {}, deletingItemUri = deleting,
                    onTrash = { item ->
                        deleting = item.uriKey
                        scope.launch {
                            try {
                                bin.trash(listOf(item))
                                shown = shown.filterNot { it.uriKey == item.uriKey }
                                current = when (item.name) { "b.mp4" -> items[2]; "c.mp4" -> items[0]; else -> null }
                            } catch (error: Throwable) { failure = error }
                            finally { deleting = null }
                        }
                    },
                )
            }
            fun awaitVideo(item: MediaItem) {
                rule.waitUntil(20000) {
                    failure?.let { throw AssertionError("Trash failed", it) }
                    rule.onAllNodesWithTag("viewer-video").fetchSemanticsNodes().any { node ->
                        node.config.getOrElse(ViewerCurrentUri) { "" } == item.uriKey && node.config.getOrElse(ViewerVideoReady) { false }
                    } && rule.onNodeWithTag("viewer-root").fetchSemanticsNode().config[ViewerCurrentUri] == item.uriKey
                }
            }
            awaitVideo(items[1])
            rule.onNodeWithTag("viewer-root").performTouchInput { doubleClick(center) }
            awaitVideo(items[2])
            assertFalse(File(items[1].sourcePath).exists())
            rule.onNodeWithTag("viewer-root").performTouchInput { doubleClick(center) }
            awaitVideo(items[0])
            assertFalse(File(items[2].sourcePath).exists())
            rule.onNodeWithTag("viewer-root").performTouchInput { doubleClick(center) }
            rule.waitUntil(15000) { current == null }
            rule.onNodeWithText("Папка пуста").assertExists()
            assertFalse(File(items[0].sourcePath).exists())
        } finally { directory.deleteRecursively() }
    }

    @Test fun ordinaryDeleteCanBeCancelledAndConfirmedExactlyOnce() {
        var visible by mutableStateOf(true)
        var confirmations = 0
        rule.setContent {
            if (visible) TrashConfirmationDialog(listOf("video.mp4"), { confirmations++; visible = false }, { visible = false })
        }
        rule.onNodeWithText("Отмена").performClick()
        assertEquals(0, confirmations)
        rule.runOnIdle { visible = true }
        rule.onNodeWithTag("confirm-trash").performClick()
        assertEquals(1, confirmations)
        rule.onNodeWithText("Удалить файл?").assertDoesNotExist()
    }

    @Test fun quickVideoInformationContainsTheActualPathInItsOwnTextNode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "path-information.mp4")
        instrumentation.context.assets.open("crop-video.mp4").use { source -> file.outputStream().use { source.copyTo(it) } }
        val item = MediaItem(987660, Uri.fromFile(file), file.name, "video/mp4", MediaKind.VIDEO, 0, 320, 240, 4000,
            "Tests", "", file.length(), sourcePath = file.path)
        rule.setContent { QuickExifOverlay(item) }
        rule.waitUntil(15000) { rule.onAllNodesWithText(file.path).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(file.path).assertExists()
        rule.onNodeWithText(file.name).assertExists()
    }
}

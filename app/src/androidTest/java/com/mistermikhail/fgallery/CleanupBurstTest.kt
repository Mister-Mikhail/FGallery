package com.mistermikhail.fgallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.CatalogDatabase
import com.mistermikhail.fgallery.data.CatalogStore
import com.mistermikhail.fgallery.data.DriveRecycleBin
import com.mistermikhail.fgallery.data.StorageAccess
import com.mistermikhail.fgallery.ui.GalleryViewModel
import com.mistermikhail.fgallery.ui.ViewerCurrentUri
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CleanupBurstTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun sequentialDoubleTapsAdvanceWhileRoomIsBusy() {
        assertTrue("Grant all-files access before starting the test process", Environment.isExternalStorageManager())
        val preferences = context.getSharedPreferences("fgallery_onboarding", Context.MODE_PRIVATE)
        val completed = preferences.getBoolean("access_wizard_completed", false)
        val nonce = UUID.randomUUID().toString()
        val root = Environment.getExternalStorageDirectory()
        val album = File(root, "Pictures/Cleanup-$nonce")
        val bin = File(root, StorageAccess.BIN)
        val executor = Executors.newSingleThreadExecutor()
        val releaseWriter = CountDownLatch(1)

        try {
            preferences.edit().putBoolean("access_wizard_completed", true).commit()
            assertTrue(album.mkdirs())
            val files = (0..7).associate { index ->
                val photo = File(album, "cleanup-$nonce-$index.jpg")
                val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.rgb(20 + 20 * index, 90, 130))
                    photo.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
                } finally {
                    bitmap.recycle()
                }
                photo.name to photo
            }

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var viewModel: GalleryViewModel
                scenario.onActivity { viewModel = ViewModelProvider(it)[GalleryViewModel::class.java] }
                rule.waitUntil(20_000) { rule.onAllNodesWithText(album.name).fetchSemanticsNodes().size == 1 }
                rule.onNodeWithText(album.name).performClick()
                rule.waitUntil(10_000) { viewModel.uiState.value.visibleItems.count { it.name in files } == files.size }
                val ordered = viewModel.uiState.value.visibleItems
                assertEquals(files.size, ordered.size)
                rule.onNodeWithContentDescription("Включить режим уборки").performClick()
                rule.onNodeWithText("Включить").performClick()
                rule.waitUntil(8_000) {
                    rule.onAllNodesWithContentDescription(ordered.first().name).fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithContentDescription(ordered.first().name).performClick()
                val viewer = rule.onNodeWithTag("viewer-root")
                fun shows(uri: String) = rule.onAllNodesWithTag("viewer-root").fetchSemanticsNodes()
                    .any { it.config[ViewerCurrentUri] == uri }
                rule.waitUntil(8_000) { shows(ordered.first().uriKey) }
                rule.mainClock.advanceTimeBy(400)
                viewer.performTouchInput { swipeLeft() }
                rule.waitUntil(8_000) { shows(ordered[1].uriKey) }

                val database = CatalogDatabase.get(context)
                val writerEntered = CountDownLatch(1)
                val transaction = executor.submit {
                    database.runInTransaction(Runnable {
                        writerEntered.countDown()
                        check(releaseWriter.await(45, TimeUnit.SECONDS))
                    })
                }
                try {
                    assertTrue("Room writer did not start", writerEntered.await(5, TimeUnit.SECONDS))
                    for (index in 1 until ordered.size) {
                        val source = files.getValue(ordered[index].name)
                        val bytes = source.readBytes()
                        val next = ordered.getOrElse(index + 1) { ordered.first() }
                        val started = SystemClock.elapsedRealtime()
                        viewer.performTouchInput { doubleClick(center) }
                        rule.waitUntil(2_000) { shows(next.uriKey) && !source.exists() }
                        val elapsed = SystemClock.elapsedRealtime() - started
                        android.util.Log.i("FGalleryCleanupQA", "sequence=$index,tap_to_next_ms=$elapsed")
                        assertFalse("A Room transaction completed during the check", transaction.isDone)
                        val copied = bin.listFiles().orEmpty().single { it.name.endsWith("_${source.name}") }
                        assertArrayEquals(bytes, copied.readBytes())
                    }
                    assertTrue(files.getValue(ordered.first().name).exists())
                } finally {
                    releaseWriter.countDown()
                    transaction.get(5, TimeUnit.SECONDS)
                }
                runBlocking {
                    withTimeout(8_000) {
                        while (database.catalog().allMedia().count { it.recycled && it.name in files } != 7) delay(50)
                    }
                }
            }
        } finally {
            releaseWriter.countDown()
            executor.shutdownNow()
            val payloads = bin.listFiles().orEmpty().filter { it.name.contains("_cleanup-$nonce-") }
            val uris = payloads.map { Uri.fromFile(it).toString() }
            payloads.forEach(File::delete)
            try {
                runBlocking {
                    DriveRecycleBin(context).deleted(uris)
                    val rows = CatalogDatabase.get(context).catalog().allMedia()
                    CatalogStore.removeUris(context, rows.filter { it.name.startsWith("cleanup-$nonce-") }.mapTo(hashSetOf()) { it.uri })
                }
            } finally {
                album.deleteRecursively()
                preferences.edit().putBoolean("access_wizard_completed", completed).commit()
            }
        }
    }

}

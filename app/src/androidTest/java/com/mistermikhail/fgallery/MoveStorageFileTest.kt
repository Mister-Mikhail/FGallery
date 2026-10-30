package com.mistermikhail.fgallery

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MoveStorageFileTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun conflictRenamesAndUndoRestoresOriginalWithoutOverwriting() = runBlocking {
        val base = File(context.cacheDir, "move-test-${System.nanoTime()}")
        val from = File(base, "src").apply { mkdirs() }
        val to = File(base, "target").apply { mkdirs() }
        try {
            File(to, "img_1234.JPG").writeText("keep existing")
            val source = File(from, "IMG_1234.jpg").apply { writeText("original") }
            val item = MediaItem(1L, Uri.fromFile(source), source.name, "image/jpeg", MediaKind.IMAGE,
                0L, 10, 10, 0L, "src", "", source.length())
            val movedUri = StorageAccess.move(context, item, to.absolutePath)
            val moved = File(requireNotNull(movedUri.path))
            assertEquals("IMG_1234_2.jpg", moved.name)
            assertEquals("original", moved.readText())
            assertEquals("keep existing", File(to, "img_1234.JPG").readText())
            assertFalse(source.exists())
            val restored = StorageAccess.move(context,
                item.copy(uri = movedUri, name = moved.name), from.absolutePath, item.name)
            assertEquals("IMG_1234.jpg", File(requireNotNull(restored.path)).name)
            assertEquals("original", source.readText())
            assertFalse(moved.exists())
        } finally { base.deleteRecursively() }
    }
}

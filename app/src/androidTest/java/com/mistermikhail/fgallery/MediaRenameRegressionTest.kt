package com.mistermikhail.fgallery

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MediaRenameRegressionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun multipleRenamesKeepExtensionsAndNeverOverwriteCollision() {
        val dir = File(context.filesDir, "rename-test-${System.nanoTime()}").apply { mkdirs() }
        val root = StorageRoot("external_primary", "Internal", dir.path, dir)
        try {
            val originals = listOf("photo.dng", "photo.nef", "photo.jpg")
            val items = originals.mapIndexed { index, name ->
                val file = File(dir, "file${index}_$name").apply { writeText("content-$index") }
                DriveLibrary.item(Uri.fromFile(file), file.name, "image/jpeg", file.length(), 0L, root, dir.path)!!
            }
            val targets = listOf("Trip_1.dng", "Trip_2.nef", "Trip_3.jpg")
            for ((item, name) in items.zip(targets)) MediaRename.rename(context, item, name)
            targets.forEachIndexed { index, name ->
                assertEquals("content-$index", File(dir, name).readText())
            }
            // Renaming to an existing target must not delete either original or target.
            val additional = File(dir, "another.jpg").apply { writeText("preserve-original") }
            val item = DriveLibrary.item(Uri.fromFile(additional), additional.name,
                "image/jpeg", additional.length(), 0L, root, dir.path)!!
            try {
                MediaRename.rename(context, item, targets.last())
                fail("Duplicate filename should fail without overwriting")
            } catch (_: IllegalStateException) {
                assertEquals("preserve-original", additional.readText())
                assertEquals("content-2", File(dir, targets.last()).readText())
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}

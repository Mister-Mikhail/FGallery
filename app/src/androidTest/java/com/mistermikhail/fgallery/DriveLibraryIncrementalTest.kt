package com.mistermikhail.fgallery

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.DriveLibrary
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.StorageRoot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveLibraryIncrementalTest {
    @Test fun safDeduplicationIsLinearAndPreservesOpaqueDocumentUris() {
        val indexed = setOf("/storage/emulated/0/DCIM/indexed.jpg")
        val seen = hashSetOf("/storage/emulated/0/DCIM/direct.jpg")
        assertFalse(DriveLibrary.shouldIncludeSafPhysicalPath(
            "/storage/emulated/0/DCIM/indexed.jpg", indexed, seen,
        ))
        assertFalse(DriveLibrary.shouldIncludeSafPhysicalPath(
            "/storage/emulated/0/DCIM/direct.jpg", indexed, seen,
        ))
        val newPath = "/storage/usb/DCIM/unique.iiq"
        assertTrue(DriveLibrary.shouldIncludeSafPhysicalPath(newPath, indexed, seen))
        assertFalse(DriveLibrary.shouldIncludeSafPhysicalPath(newPath, indexed, seen))
        // An opaque provider cannot be matched to a physical path; its
        // document URI remains the unique catalogue identity.
        assertTrue(DriveLibrary.shouldIncludeSafPhysicalPath(null, indexed, seen))
        assertTrue(DriveLibrary.shouldIncludeSafPhysicalPath(null, indexed, seen))
        assertEquals(2, seen.size)
    }

    @Test fun unchangedFolderTimestampStillDetectsNewOverwrittenAndDeletedRawFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "fgallery-folder-delta-${System.nanoTime()}")
        assertTrue(dir.mkdirs())
        try {
            val root = StorageRoot("external_primary", "Phone", dir.path, dir)
            val overwritten = File(dir, "frame.3fr").apply { writeBytes(byteArrayOf(1, 2)) }
            val deleted = File(dir, "gone.iiq").apply { writeBytes(byteArrayOf(3, 4)) }
            fun cached(file: File) = requireNotNull(DriveLibrary.item(
                Uri.fromFile(file), file.name, DriveLibrary.mime(file.name),
                file.length(), file.lastModified(), root, dir.path,
            ))
            val prior = listOf(cached(overwritten), cached(deleted))
            val oldFolderMtime = dir.lastModified()

            assertTrue(deleted.delete())
            overwritten.writeBytes(ByteArray(17) { 7 })
            val added = File(dir, "new.cr2").apply { writeBytes(byteArrayOf(8, 9, 10)) }
            // Simulate a coarse FAT/exFAT timestamp or a provider that did
            // not update the parent folder after changes.
            assertTrue(dir.setLastModified(oldFolderMtime))

            val found = DriveLibrary.reconcileUnchangedFolder(
                requireNotNull(dir.listFiles()), emptySet(), prior, root, dir,
            )
            assertEquals(setOf("frame.3fr", "new.cr2"), found.map { it.name }.toSet())
            assertEquals(17L, found.single { it.name == "frame.3fr" }.sizeBytes)
            assertTrue(found.all { it.kind == MediaKind.RAW })
            assertFalse(found.any { it.name == "gone.iiq" })

            // Files already indexed by MediaStore must not appear twice.
            val deduped = DriveLibrary.reconcileUnchangedFolder(
                requireNotNull(dir.listFiles()), setOf(added.path), prior, root, dir,
            )
            assertEquals(listOf("frame.3fr"), deduped.map { it.name })
        } finally {
            dir.deleteRecursively()
        }
    }
}

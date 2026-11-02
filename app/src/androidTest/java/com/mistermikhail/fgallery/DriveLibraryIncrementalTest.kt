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


    @Test fun unchangedSafDocumentRetainsEnrichedMetadataButChangedVersionDoesNot() {
        val root = StorageRoot("external_primary", "Phone", "/storage/emulated/0", null)
        val uri = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3A/document/primary%3ADCIM%2Fphoto.dng"
        )
        val fresh = requireNotNull(DriveLibrary.item(
            uri, "photo.dng", "image/x-adobe-dng", 1024L, 1234L,
            root, "content://com.android.externalstorage.documents/tree/primary%3A", "DCIM/",
        ))
        val prior = fresh.copy(dateTakenMillis = 111L, width = 4000,
            height = 3000, durationMillis = 50L)
        val reused = DriveLibrary.reuseUnchangedSafMetadata(fresh, prior)
        assertEquals(4000, reused.width)
        assertEquals(3000, reused.height)
        assertEquals(111L, reused.dateTakenMillis)
        assertEquals(50L, reused.durationMillis)
        assertEquals(fresh, DriveLibrary.reuseUnchangedSafMetadata(fresh, null))
        assertEquals(fresh.copy(sizeBytes = 1025L),
            DriveLibrary.reuseUnchangedSafMetadata(fresh.copy(sizeBytes = 1025L), prior))
        assertEquals(fresh.copy(dateModifiedMillis = 1235L),
            DriveLibrary.reuseUnchangedSafMetadata(fresh.copy(dateModifiedMillis = 1235L), prior))
        assertEquals(fresh.copy(name = "renamed.dng"),
            DriveLibrary.reuseUnchangedSafMetadata(fresh.copy(name = "renamed.dng"), prior))
        assertEquals(fresh.copy(folderTarget = "content://different-folder"),
            DriveLibrary.reuseUnchangedSafMetadata(
                fresh.copy(folderTarget = "content://different-folder"), prior))
        assertEquals(fresh.copy(sourcePath = "/storage/usb/photo.dng"),
            DriveLibrary.reuseUnchangedSafMetadata(
                fresh.copy(sourcePath = "/storage/usb/photo.dng"), prior))
        assertEquals(fresh.copy(storageName = "Renamed volume"),
            DriveLibrary.reuseUnchangedSafMetadata(
                fresh.copy(storageName = "Renamed volume"), prior))
        assertEquals(fresh, DriveLibrary.reuseUnchangedSafMetadata(
            fresh, prior.copy(dateModifiedMillis = 0L)))
    }

    @Test fun safTraversalStopsProviderCyclesWithoutCollidingAcrossAuthorities() {
        val seen = hashSetOf<String>()
        assertTrue(DriveLibrary.markSafDocumentVisited(
            "com.android.externalstorage.documents", "primary:DCIM", seen,
        ))
        assertTrue(DriveLibrary.markSafDocumentVisited(
            "com.android.externalstorage.documents", "primary:DCIM/sub", seen,
        ))
        // A provider can return an ancestor or itself as a child.
        assertFalse(DriveLibrary.markSafDocumentVisited(
            "com.android.externalstorage.documents", "primary:DCIM", seen,
        ))
        // IDs are provider-scoped: an unrelated SAF provider can use the
        // same document ID without being incorrectly suppressed.
        assertTrue(DriveLibrary.markSafDocumentVisited(
            "example.cloud.provider", "primary:DCIM", seen,
        ))
        assertEquals(3, seen.size)
    }

    @Test fun safPathMappingRejectsOpaqueProvidersWrongVolumesAndTraversal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "saf-mapping-root").apply { mkdirs() }
        val external = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")
        val cloud = Uri.parse("content://example.cloud.provider/tree/primary%3A")
        try {
            val expected = File(root, "DCIM/frame.dng").canonicalPath
            assertEquals(expected, DriveLibrary.trustedSafPhysicalPath(
                root, external, "primary:DCIM/frame.dng", "external_primary",
            ))
            assertEquals(null, DriveLibrary.trustedSafPhysicalPath(
                root, cloud, "primary:DCIM/frame.dng", "external_primary",
            ))
            assertEquals(null, DriveLibrary.trustedSafPhysicalPath(
                root, external, "ABCD-1234:DCIM/frame.dng", "external_primary",
            ))
            assertEquals(null, DriveLibrary.trustedSafPhysicalPath(
                root, external, "primary:../../outside.dng", "external_primary",
            ))
            assertEquals(null, DriveLibrary.trustedSafPhysicalPath(
                root, external, "opaque-id-without-volume", "external_primary",
            ))
            assertEquals(null, DriveLibrary.trustedSafPhysicalPath(
                null, external, "primary:DCIM/frame.dng", "external_primary",
            ))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun physicalTraversalStaysInsideMountAndRejectsCanonicalAliases() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = File(context.cacheDir, "fgallery-mount-safety-${System.nanoTime()}")
        val root = File(base, "mount")
        val child = File(root, "DCIM")
        val outside = File(base, "outside")
        try {
            assertTrue(child.mkdirs())
            assertTrue(outside.mkdirs())
            val visited = hashSetOf<String>()
            assertTrue(DriveLibrary.markPhysicalDirectoryVisited(root, root, visited))
            assertTrue(DriveLibrary.markPhysicalDirectoryVisited(root, child, visited))
            assertFalse(DriveLibrary.markPhysicalDirectoryVisited(root, child, visited))
            // A path containing '..' resolves to a previously visited root.
            assertFalse(DriveLibrary.markPhysicalDirectoryVisited(
                root, File(child, ".."), visited,
            ))
            // A mounted provider must never scan a sibling directory.
            assertFalse(DriveLibrary.markPhysicalDirectoryVisited(root, outside, visited))
            assertFalse(DriveLibrary.markPhysicalDirectoryVisited(
                root, File(root, "../outside"), visited,
            ))
            assertEquals(2, visited.size)
        } finally {
            base.deleteRecursively()
        }
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

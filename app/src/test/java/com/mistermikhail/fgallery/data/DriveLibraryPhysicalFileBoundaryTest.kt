package com.mistermikhail.fgallery.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class DriveLibraryPhysicalFileBoundaryTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun onlyFilesInsideTheExactMountedRootAreIndexed() {
        val mount = temp.newFolder("usb")
        val sibling = temp.newFolder("usb-backup")
        val inside = File(mount, "photo.jpg").apply { writeBytes(byteArrayOf(1)) }
        val outside = File(sibling, "other.jpg").apply { writeBytes(byteArrayOf(2)) }
        val canonicalRoot = mount.canonicalPath

        assertTrue(DriveLibrary.isPhysicalFileInsideRoot(canonicalRoot, inside))
        assertFalse(DriveLibrary.isPhysicalFileInsideRoot(canonicalRoot, outside))
        assertFalse(DriveLibrary.isPhysicalFileInsideRoot(canonicalRoot, mount))
        assertFalse(DriveLibrary.isPhysicalFileInsideRoot(canonicalRoot, File(mount, "missing.jpg")))
    }

    @Test fun symlinkToFileOutsideMountedRootIsNotIndexed() {
        val mount = temp.newFolder("drive")
        val outside = File(temp.newFolder("private"), "private.jpg").apply {
            writeBytes(byteArrayOf(3))
        }
        val link = File(mount, "link.jpg")
        Files.createSymbolicLink(link.toPath(), outside.toPath())

        assertTrue(link.isFile)
        assertFalse(DriveLibrary.isPhysicalFileInsideRoot(mount.canonicalPath, link))
    }
}

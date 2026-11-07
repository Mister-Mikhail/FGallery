package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Some SAF providers return null or application/octet-stream for media. */
class DriveLibraryKindTest {
    @Test fun missingMimeStillDiscoversCommonImages() {
        for (extension in listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "bmp")) {
            assertEquals(extension, MediaKind.IMAGE, DriveLibrary.kind("PHOTO.$extension", null))
            assertEquals(extension, MediaKind.IMAGE, DriveLibrary.kind("PHOTO.$extension", "application/octet-stream"))
        }
    }

    @Test fun missingMimeStillDiscoversCommonVideos() {
        for (extension in listOf("mp4", "m4v", "mov", "mkv", "webm", "3gp", "avi")) {
            assertEquals(extension, MediaKind.VIDEO, DriveLibrary.kind("VIDEO.$extension", null))
        }
    }

    @Test fun rawAndDocumentKindsKeepTheirPriority() {
        assertEquals(MediaKind.RAW, DriveLibrary.kind("camera.3FR", "application/octet-stream"))
        assertEquals(MediaKind.PDF, DriveLibrary.kind("pages.PDF", null))
        assertEquals(MediaKind.SVG, DriveLibrary.kind("vector.SVG", null))
        assertNull(DriveLibrary.kind("notes.txt", "application/octet-stream"))
    }
}

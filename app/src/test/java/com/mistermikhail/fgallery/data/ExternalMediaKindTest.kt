package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalMediaKindTest {
    @Test fun aviAndM4vWithGenericMimeAreVideos() {
        for (extension in listOf("AVI", "avi", "M4V", "m4v")) {
            assertEquals(MediaKind.VIDEO, ExternalMediaKind.classify("clip.$extension", "application/octet-stream"))
        }
    }

    @Test fun avifAndCameraRawWithGenericMimeAreRecognized() {
        assertEquals(MediaKind.IMAGE, ExternalMediaKind.classify("photo.AVIF", null))
        for (extension in listOf("DNG", "CR3", "NEF", "ARW", "RAF", "RW2")) {
            assertEquals(MediaKind.RAW, ExternalMediaKind.classify("photo.$extension", "application/octet-stream"))
        }
    }

    @Test fun extensionOverridesIncorrectProviderMime() {
        assertEquals(MediaKind.VIDEO, ExternalMediaKind.classify("clip.avi", "image/jpeg"))
        assertEquals(MediaKind.RAW, ExternalMediaKind.classify("photo.dng", "image/jpeg"))
    }

    @Test fun mimeFallbackWorksWithoutExtension() {
        assertEquals(MediaKind.VIDEO, ExternalMediaKind.classify("12345", "video/mp4"))
        assertEquals(MediaKind.IMAGE, ExternalMediaKind.classify("12345", "image/avif"))
    }

    @Test fun arbitraryBinaryDoesNotBecomeImage() {
        assertNull(ExternalMediaKind.classify("archive.zip", "application/octet-stream"))
    }
}

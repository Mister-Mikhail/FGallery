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

    @Test fun media3SupportedContainerExtensionsAreRecognizedFromSafNames() {
        for (extension in listOf("m4p", "M4P", "mk3d", "MK3D", "3gpp", "3GPP")) {
            assertEquals("clip.$extension", MediaKind.VIDEO,
                ExternalMediaKind.classify("clip.$extension", "application/octet-stream"))
            assertEquals("clip.$extension", MediaKind.VIDEO,
                ExternalMediaKind.classify("clip.$extension", "image/jpeg"))
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

    @Test fun extensionlessSafSvgUsesSvgViewerNotGenericImageDecoder() {
        assertEquals(MediaKind.SVG, ExternalMediaKind.classify("document", "image/svg+xml"))
        assertEquals(MediaKind.SVG, ExternalMediaKind.classify("document", "IMAGE/SVG+XML"))
        assertEquals(MediaKind.SVG, ExternalMediaKind.classify("vector.svg", "image/jpeg"))
        assertEquals(MediaKind.IMAGE, ExternalMediaKind.classify("photo.avif", "image/svg+xml"))
    }

    @Test fun extensionlessSafVideoRecognizesLegacyContainerMimeAliases() {
        for (mime in listOf("application/x-avi", "application/avi", "application/x-m4v",
                "APPLICATION/X-AVI", "application/x-avi; charset=binary", "video/x-msvideo")) {
            assertEquals(mime, MediaKind.VIDEO, ExternalMediaKind.classify("document", mime))
        }
        assertEquals(MediaKind.VIDEO, ExternalMediaKind.classify("clip.m4v ", "application/octet-stream"))
        assertNull(ExternalMediaKind.classify("document", "application/octet-stream"))
    }

    @Test fun arbitraryBinaryDoesNotBecomeImage() {
        assertNull(ExternalMediaKind.classify("archive.zip", "application/octet-stream"))
    }
}

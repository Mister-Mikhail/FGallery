package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlaybackMimeTest {
    @Test fun m4vUsesMp4ExtractorEvenWhenProviderReportsGenericMime() {
        assertEquals("video/mp4", VideoPlaybackMime.forFile("clip.M4V", "application/octet-stream"))
        assertEquals("video/mp4", VideoPlaybackMime.forFile("clip.m4v", null))
        assertEquals("video/mp4", VideoPlaybackMime.forFile("clip.mp4", "video/quicktime"))
        assertEquals("video/mp4", VideoPlaybackMime.forFile("clip.MOV", "application/octet-stream"))
    }

    @Test fun m4vAndM4pOverrideMisreportedImageMime() {
        for (extension in listOf("m4v", "M4V", "m4p", "M4P")) {
            assertEquals("clip.$extension", "video/mp4",
                VideoPlaybackMime.forFile("clip.$extension", "image/jpeg"))
            assertEquals("clip.$extension", "video/mp4",
                VideoPlaybackMime.forFile("clip.$extension", "application/octet-stream"))
        }
    }

    @Test fun aviKeepsItsContainerIdentityAndDoesNotPretendToBeMp4() {
        assertEquals("video/avi", VideoPlaybackMime.forFile("clip.AVI", null))
        assertEquals("video/avi", VideoPlaybackMime.forFile("clip.avi", "image/jpeg"))
    }

    @Test fun knownContainersOverrideIncorrectSafMime() {
        val expected = mapOf(
            "clip.mkv" to "video/x-matroska",
            "clip.WEBM" to "video/webm",
            "clip.3gp" to "video/3gpp",
            "clip.m2ts" to "video/mp2t",
            "clip.mts" to "video/mp2t",
        )
        expected.forEach { (name, mime) ->
            assertEquals(name, mime, VideoPlaybackMime.forFile(name, "application/octet-stream"))
            assertEquals(name, mime, VideoPlaybackMime.forFile(name, "image/jpeg"))
        }
    }

    @Test fun legacyVideoExtensionsUseContainerMimeDespiteIncorrectSafMime() {
        val expected = mapOf(
            "clip.WMV" to "video/x-ms-wmv",
            "clip.mpeg" to "video/mpeg",
            "clip.MPG" to "video/mpeg",
        )
        expected.forEach { (name, mime) ->
            assertEquals(name, mime, VideoPlaybackMime.forFile(name, "application/octet-stream"))
            assertEquals(name, mime, VideoPlaybackMime.forFile(name, "image/jpeg"))
            assertEquals(name, MediaKind.VIDEO, ExternalMediaKind.classify(name, "image/jpeg"))
        }
    }

    @Test fun unknownFormatsDoNotMasqueradeAsVideo() {
        assertEquals("video/avi", VideoPlaybackMime.forFile("unknown.bin", "video/x-msvideo"))
        assertEquals(null, VideoPlaybackMime.forFile("unknown.bin", "application/octet-stream"))
        assertEquals(null, VideoPlaybackMime.forFile("song.m4a", "audio/mp4"))
    }
    @Test fun extensionlessSafVideoMimeIsNormalizedForPlayback() {
        assertEquals("video/mp4", VideoPlaybackMime.forFile("content", " Application/X-M4V ; charset=utf-8 "))
        assertEquals("video/mp4", VideoPlaybackMime.forFile("content", "video/quicktime"))
        for (mime in listOf("application/x-avi", "application/avi", "application/x-msvideo", " APPLICATION/X-MSVIDEO ; charset=binary ", "VIDEO/X-MSVIDEO; foo=bar", " video/msvideo ; charset=binary ")) {
            assertEquals(mime, "video/avi", VideoPlaybackMime.forFile("content", mime))
        }
        assertEquals("video/mp4", VideoPlaybackMime.forFile("movie.M4V  ", "image/jpeg"))
        assertEquals(null, VideoPlaybackMime.forFile("content", "application/octet-stream"))
    }

}

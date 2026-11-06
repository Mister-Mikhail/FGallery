package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlaybackMimeTest {
    @Test fun m4vUsesMp4ExtractorEvenWhenProviderReportsGenericMime() {
        assertEquals("video/mp4", VideoPlaybackMime.forFile("clip.M4V", "application/octet-stream"))
        assertEquals("video/mp4", VideoPlaybackMime.forFile("clip.m4v", null))
        assertEquals("video/mp4", VideoPlaybackMime.forFile("clip.mp4", "video/quicktime"))
    }

    @Test fun aviKeepsItsContainerIdentityAndDoesNotPretendToBeMp4() {
        assertEquals("video/avi", VideoPlaybackMime.forFile("clip.AVI", null))
    }

    @Test fun otherVideosKeepProviderMimeWhenValid() {
        assertEquals("video/x-matroska", VideoPlaybackMime.forFile("clip.mkv", "video/x-matroska"))
        assertEquals(null, VideoPlaybackMime.forFile("unknown.bin", "application/octet-stream"))
    }
}

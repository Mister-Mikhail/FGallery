package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawThumbnailPolicyTest {
    @Test fun proprietaryRawDoesNotWaitForPlatformDemosaic() {
        assertTrue(ThumbnailCache.skipPlatformRawThumbnail("PhaseOne.IIQ"))
        assertTrue(ThumbnailCache.skipPlatformRawThumbnail("Hasselblad.3fr"))
        assertTrue(ThumbnailCache.skipPlatformRawThumbnail("Hasselblad.FFF"))
        assertFalse(ThumbnailCache.skipPlatformRawThumbnail("Samsung.dng"))
        assertFalse(ThumbnailCache.skipPlatformRawThumbnail("Canon.cr2"))
        assertFalse(ThumbnailCache.skipPlatformRawThumbnail("portrait.jpg"))
    }

    @Test fun albumModificationUsesNewestAvailableTimestamp() {
        assertEquals(1_700_000_002_000L, effectiveModifiedTimestamp(1_700_000_000_000L, 1_700_000_002_000L))
        assertEquals(1_700_000_002_000L, effectiveModifiedTimestamp(1_700_000_002_000L, 1_700_000_000_000L))
        assertEquals(1_700_000_000_000L, effectiveModifiedTimestamp(1_700_000_000_000L, 0L))
        assertEquals(0L, effectiveModifiedTimestamp(-1L, 0L))
    }
}

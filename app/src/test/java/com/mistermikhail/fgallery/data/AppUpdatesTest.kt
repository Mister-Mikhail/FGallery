package com.mistermikhail.fgallery.data

import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    @Test fun semanticVersionsCompareNumericallyAndRejectDowngrades() {
        assertTrue(AppUpdates.isNewerVersion("0.2.10", "0.2.9"))
        assertTrue(AppUpdates.isNewerVersion("v1.0.0", "0.99.99"))
        assertFalse(AppUpdates.isNewerVersion("0.2.6", "0.2.6"))
        assertFalse(AppUpdates.isNewerVersion("0.2.5", "0.2.6"))
        assertFalse(AppUpdates.isNewerVersion("0.2.7-beta", "0.2.6"))
        assertFalse(AppUpdates.isNewerVersion("invalid", "0.2.6"))
    }

    @Test fun qaReleaseTagsUseBuildCodeNotVersionName() {
        assertEquals("0.3.0" to 24L, AppUpdates.releaseVersionAndCode("qa-v0.3.0-b24", "", "0.3.0", 23))
        assertEquals("0.3.0" to 23L, AppUpdates.releaseVersionAndCode("qa-v0.3.0-b23", "", "0.3.0", 24))
        assertEquals("0.3.1" to 25L, AppUpdates.releaseVersionAndCode("qa-v0.3.1-b25", "", "0.3.0", 24))
        assertNull(AppUpdates.releaseVersionAndCode("qa-v0.3.0-bx", "", "0.3.0", 23))
        assertNull(AppUpdates.releaseVersionAndCode("random-tag", "", "0.3.0", 23))
    }

    @Test fun stableReleaseSupportsMarkedCodeAndOldTagFallback() {
        assertEquals("0.3.1" to 25L,
            AppUpdates.releaseVersionAndCode("v0.3.1", "FGALLERY_VERSION_CODE=25\n", "0.3.0", 24))
        assertEquals("0.2.9" to 11L,
            AppUpdates.releaseVersionAndCode("v0.2.9", "", "0.2.8", 10))
        assertNull(AppUpdates.releaseVersionAndCode("v0.2.9", "", "0.3.0", 23))
        assertNull(AppUpdates.releaseVersionAndCode("v0.3.0-beta", "", "0.2.9", 11))
    }
}

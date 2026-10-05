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
}

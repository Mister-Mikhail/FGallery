package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogVolumeTest {
    @Test fun mountedEmptyVolumeStaysConnectedWithItsGeneration() {
        val atStart = CatalogVolumeRow(
            storageId = "external_primary",
            displayName = "Internal",
            mediaStoreGeneration = 42L,
            mediaStoreVersion = "v3",
            lastScannedMillis = 1L,
            connected = true,
        )

        // A gallery may have no indexed media even while its volume is mounted.
        val rows = CatalogStore.activeVolumeRows(emptyMap(), mapOf(atStart.storageId to atStart), 500L)

        assertEquals(1, rows.size)
        assertEquals(42L, rows.single().mediaStoreGeneration)
        assertEquals("v3", rows.single().mediaStoreVersion)
        assertEquals(500L, rows.single().lastScannedMillis)
        assertTrue(rows.single().connected)
    }

    @Test fun mediaOnlyVolumeAndEmptyIndexedVolumeAreBothPreserved() {
        val atStart = CatalogVolumeRow("external_primary", "Internal", 8L, "v1", 0L, true)
        val rows = CatalogStore.activeVolumeRows(
            mapOf("usb" to "USB drive"),
            mapOf(atStart.storageId to atStart),
            900L,
        ).associateBy { it.storageId }

        assertEquals(setOf("external_primary", "usb"), rows.keys)
        assertEquals(8L, rows.getValue("external_primary").mediaStoreGeneration)
        assertEquals(0L, rows.getValue("usb").mediaStoreGeneration)
        assertEquals("USB drive", rows.getValue("usb").displayName)
        assertTrue(rows.values.all { it.connected })
    }
}

package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogMediaDeltaTest {
    private val scanned = CatalogMediaRow(
        uri = "media://one", mediaId = 1L, name = "photo.jpg",
        mimeType = "image/jpeg", kind = "IMAGE", dateTakenMillis = 10L,
        width = 4000, height = 3000, durationMillis = 0L,
        album = "DCIM", relativePath = "DCIM/", sizeBytes = 1000L,
        dateModifiedMillis = 100L, storageId = "external_primary",
        storageName = "Phone", folderTarget = "/phone/DCIM",
        sourcePath = "/phone/DCIM/photo.jpg",
    )

    @Test fun hashFreeRescanDoesNotRewriteAnIdenticalCachedRow() {
        val cached = scanned.copy(quickHash = "edge", fullHash = "full",
            perceptualHash = "visual", exifCamera = "Canon")
        assertTrue(changedMediaRows(mapOf(cached.uri to cached), listOf(scanned)).isEmpty())
    }

    @Test fun renameRetainsHashesButChangedFileInvalidatesThem() {
        val cached = scanned.copy(quickHash = "edge", fullHash = "full",
            perceptualHash = "visual", exifCamera = "Canon")
        val renamed = changedMediaRows(mapOf(cached.uri to cached),
            listOf(scanned.copy(name = "renamed.jpg"))).single()
        assertEquals("renamed.jpg", renamed.name)
        assertEquals("full", renamed.fullHash)
        assertEquals("visual", renamed.perceptualHash)
        val replaced = changedMediaRows(mapOf(cached.uri to cached),
            listOf(scanned.copy(sizeBytes = 1001L))).single()
        assertEquals(null, replaced.quickHash)
        assertEquals(null, replaced.fullHash)
        assertEquals(null, replaced.perceptualHash)
    }

    @Test fun unindexedNewFileIsIncluded() {
        assertEquals(listOf(scanned), changedMediaRows(emptyMap(), listOf(scanned)))
    }
}

package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DuplicateFingerprintsTest {
    private fun row(
        uri: String,
        size: Long = 100,
        modified: Long = 123,
        quick: String? = null,
        full: String? = null,
        recycled: Boolean = false,
    ) = CatalogMediaRow(
        uri = uri, mediaId = 1, name = "photo.jpg", mimeType = "image/jpeg",
        kind = "IMAGE", dateTakenMillis = 123, width = 100, height = 100,
        durationMillis = 0, album = "Camera", relativePath = "DCIM/",
        sizeBytes = size, dateModifiedMillis = modified,
        storageId = "external_primary", storageName = "Phone",
        folderTarget = "DCIM/", sourcePath = "/DCIM/photo.jpg",
        quickHash = quick, fullHash = full, recycled = recycled,
    )

    @Test fun identicalBytesHaveIdenticalQuickAndFullHashes() {
        val one = Files.createTempFile("fgallery-hash-one", ".bin").toFile()
        val two = Files.createTempFile("fgallery-hash-two", ".bin").toFile()
        try {
            val bytes = ByteArray(260_000) { (it * 13).toByte() }
            one.writeBytes(bytes)
            two.writeBytes(bytes)
            assertEquals(DuplicateFingerprints.quick(one), DuplicateFingerprints.quick(two))
            assertEquals(DuplicateFingerprints.full(one), DuplicateFingerprints.full(two))
        } finally {
            one.delete()
            two.delete()
        }
    }

    @Test fun matchingEdgesAreNotEnoughToProveExactCopy() {
        val one = Files.createTempFile("fgallery-center-one", ".bin").toFile()
        val two = Files.createTempFile("fgallery-center-two", ".bin").toFile()
        try {
            val bytes = ByteArray(256_000) { 1 }
            one.writeBytes(bytes)
            bytes[128_000] = 2
            two.writeBytes(bytes)
            assertEquals(DuplicateFingerprints.quick(one), DuplicateFingerprints.quick(two))
            assertFalse(DuplicateFingerprints.full(one) == DuplicateFingerprints.full(two))
        } finally {
            one.delete()
            two.delete()
        }
    }

    @Test fun fullHashRequiredAndTrashedFilesExcluded() {
        val original = row("file:///a", quick = "same", full = "sha")
        val copy = row("file:///b", quick = "same", full = "sha")
        val rawPair = row("file:///c", quick = "same", full = "different")
        val unfinished = row("file:///d", quick = "same")
        val trashed = row("file:///e", quick = "same", full = "sha", recycled = true)
        assertEquals(listOf(listOf(original, copy)),
            exactDuplicateGroups(listOf(original, copy, rawPair, unfinished, trashed)))
    }

    @Test fun metadataOnlyRefreshRetainsHashesButChangedBytesInvalidate() {
        val old = row("file:///a", quick = "edge", full = "complete")
            .copy(perceptualHash = "perceptual", exifCamera = "Canon")
        val renamed = old.copy(name = "renamed.jpg", quickHash = null,
            fullHash = null, perceptualHash = null, exifCamera = null)
        val retained = retainUnchangedFingerprints(old, renamed)
        assertEquals("edge", retained.quickHash)
        assertEquals("complete", retained.fullHash)
        assertEquals("perceptual", retained.perceptualHash)
        assertEquals("Canon", retained.exifCamera)

        val changed = retainUnchangedFingerprints(old, renamed.copy(dateModifiedMillis = 124))
        assertNull(changed.quickHash)
        assertNull(changed.fullHash)
        assertNull(changed.perceptualHash)
        assertNull(changed.exifCamera)
        assertNull(retainUnchangedFingerprints(old, renamed.copy(sizeBytes = 101)).fullHash)
    }

    @Test fun hashOfEmptyFileIsStable() {
        val empty = Files.createTempFile("fgallery-empty", ".bin").toFile()
        try {
            assertEquals(DuplicateFingerprints.quick(empty), DuplicateFingerprints.quick(empty))
            assertEquals(64, DuplicateFingerprints.full(empty).length)
            assertTrue(exactDuplicateGroups(listOf(row("a", size = 0, quick = "x", full = "x"),
                row("b", size = 0, quick = "x", full = "x"))).isEmpty())
        } finally {
            empty.delete()
        }
    }
}

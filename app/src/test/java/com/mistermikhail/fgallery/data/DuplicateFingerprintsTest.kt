package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.FileInputStream
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
        // Providers can preserve both file length and coarse mtime on overwrite.
        // A fresh edge hash is stronger evidence than unchanged metadata.
        val replaced = retainUnchangedFingerprints(old, renamed.copy(quickHash = "new-edge"))
        assertEquals("new-edge", replaced.quickHash)
        assertNull(replaced.fullHash)
        assertNull(replaced.perceptualHash)
    }

    @Test fun plannerHashesOnlySizeCollisionsThenEdgeCollisions() {
        val a = row("a", size = 120)
        val b = row("b", size = 120)
        val singleton = row("single", size = 121)
        val recycled = row("trash", size = 120, recycled = true)
        assertEquals(listOf(a, b), quickHashCandidates(listOf(a, b, singleton, recycled)))

        val withEdges = listOf(
            a.copy(quickHash = "same"), b.copy(quickHash = "same"),
            row("c", size = 120, quick = "other"),
            row("d", size = 120, quick = "other", full = "already"),
        )
        assertEquals(listOf(withEdges[0], withEdges[1], withEdges[2]),
            fullHashCandidates(withEdges))
    }

    @Test fun exactCopyProposalPrefersOriginalFormatThenResolutionThenCaptureDate() {
        // Only verified equal bytes may enter a group; the order is advisory.
        val jpeg = row("file:///jpeg", quick = "edge", full = "sha")
            .copy(name = "image.jpg", width = 1000, height = 1000, dateTakenMillis = 100L)
        val tiff = row("file:///tiff", quick = "edge", full = "sha")
            .copy(name = "image.tiff", width = 2000, height = 2000, dateTakenMillis = 200L)
        val raw = row("file:///raw", quick = "edge", full = "sha")
            .copy(name = "image.dng", width = 1000, height = 1000, dateTakenMillis = 300L)
        assertEquals(listOf(raw, tiff, jpeg),
            exactDuplicateGroups(listOf(jpeg, tiff, raw)).single())

        val older = jpeg.copy(uri = "file:///older", dateTakenMillis = 10L)
        val newer = jpeg.copy(uri = "file:///newer", dateTakenMillis = 20L)
        assertEquals(listOf(older, newer),
            exactDuplicateGroups(listOf(newer, older)).single())
    }

    @Test fun exactCopyGroupingDoesNotConfuseRawJpegPairOrEditedVersion() {
        val raw = row("file:///raw", quick = "same-edge", full = "raw-bytes")
            .copy(name = "frame.dng", kind = "RAW")
        val jpeg = row("file:///jpeg", quick = "same-edge", full = "jpeg-bytes")
            .copy(name = "frame.jpg")
        val retouched = row("file:///edit", quick = "same-edge", full = "edited-bytes")
            .copy(name = "frame-edited.jpg")
        assertTrue(exactDuplicateGroups(listOf(raw, jpeg, retouched)).isEmpty())
    }

    @Test fun fingerprintReadCanBeCancelledBeforeExpensiveFullHashCompletes() {
        val source = Files.createTempFile("fgallery-cancel-hash", ".bin").toFile()
        try {
            source.writeBytes(ByteArray(1024 * 1024) { 7 })
            var checks = 0
            var cancelled = false
            try {
                DuplicateFingerprints.full(source) {
                    if (++checks >= 3) throw java.util.concurrent.CancellationException()
                }
            } catch (_: java.util.concurrent.CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
            assertTrue(checks >= 3)
        } finally {
            source.delete()
        }
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
    @Test fun seekableChannelAndStreamMatchLocalFileDigests() {
        val file = Files.createTempFile("fgallery-provider-hash", ".bin").toFile()
        try {
            val bytes = ByteArray(200_000) { (it * 37).toByte() }
            file.writeBytes(bytes)
            val fromChannel = FileInputStream(file).use {
                DuplicateFingerprints.quick(it.channel)
            }
            assertEquals(DuplicateFingerprints.quick(file), fromChannel)
            assertEquals(DuplicateFingerprints.full(file),
                DuplicateFingerprints.full(ByteArrayInputStream(bytes), expectedBytes = bytes.size.toLong()))
            try {
                DuplicateFingerprints.full(ByteArrayInputStream(bytes),
                    expectedBytes = bytes.size.toLong() + 1L)
                org.junit.Assert.fail("Short provider stream must not be cached")
            } catch (_: EOFException) { /* correct */ }
        } finally {
            file.delete()
        }
    }


}

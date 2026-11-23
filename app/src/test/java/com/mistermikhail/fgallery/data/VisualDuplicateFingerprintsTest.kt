package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualDuplicateFingerprintsTest {
    private fun row(uri: String, hash: String, kind: String = "IMAGE") = CatalogMediaRow(
        uri = uri, mediaId = 1L, name = "frame.jpg", mimeType = "image/jpeg",
        kind = kind, dateTakenMillis = 1_000_000L, width = 2400, height = 1600,
        durationMillis = 0L, album = "Camera", relativePath = "DCIM/",
        sizeBytes = 100_000L, dateModifiedMillis = 1_000_000L,
        storageId = "external_primary", storageName = "Phone",
        folderTarget = "/phone/DCIM", sourcePath = "/phone/DCIM/frame.jpg",
        perceptualHash = hash, exifCamera = "Canon R5",
    )

    private fun sample(color: (Int, Int) -> Int): IntArray =
        IntArray(VisualDuplicateFingerprints.SAMPLE_PIXELS) { index ->
            val x = index % VisualDuplicateFingerprints.SAMPLE_WIDTH
            val y = index / VisualDuplicateFingerprints.SAMPLE_WIDTH
            color(x, y)
        }

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xff shl 24) or (r.coerceIn(0,255) shl 16) or
            (g.coerceIn(0,255) shl 8) or b.coerceIn(0,255)

    @Test fun knownExactCopiesStayOutOfTheVisualCandidatePage() {
        val first = row("first", "0123456789abcdef").copy(fullHash = "a".repeat(64))
        val copy = first.copy(uri = "copy")
        val similar = first.copy(uri = "similar", fullHash = "b".repeat(64))
        val pairs = visualCandidatesAcrossPages(listOf(first, copy, similar), 0)
        assertFalse(pairs.contains("first" to "copy"))
        assertTrue(pairs.contains("first" to "similar"))
    }

    @Test fun hashIsStableFixedLengthAndChecksInput() {
        val pixels = sample { x, y -> rgb(x * 20, y * 20, 40) }
        val hash = VisualDuplicateFingerprints.differenceHash(pixels)
        assertEquals(16, hash.length)
        assertEquals(hash, VisualDuplicateFingerprints.differenceHash(pixels.copyOf()))
        assertEquals(0, VisualDuplicateFingerprints.hammingDistance(hash, hash))
        assertNull(VisualDuplicateFingerprints.hammingDistance(hash, "invalid"))
        assertNull(VisualDuplicateFingerprints.hammingDistance(hash, "zzzzzzzzzzzzzzzz"))
    }

    @Test fun horizontalStructureChangesHashButColorEditMayNot() {
        val lightToDark = sample { x, _ -> rgb(240 - x * 20, 240 - x * 20, 240 - x * 20) }
        val darkToLight = sample { x, _ -> rgb(20 + x * 20, 20 + x * 20, 20 + x * 20) }
        val colorShift = sample { x, _ -> rgb(240 - x * 20, 220 - x * 20, 180 - x * 20) }
        val first = VisualDuplicateFingerprints.differenceHash(lightToDark)
        val second = VisualDuplicateFingerprints.differenceHash(darkToLight)
        val edited = VisualDuplicateFingerprints.differenceHash(colorShift)
        assertNotEquals(first, second)
        assertEquals(first, edited) // This is WHY dHash must never be deletion proof.
    }

    @Test fun resizedCandidatesRequireMetadataAndNeverIncludeRawJpegPairs() {
        val hash = "ff00ff00ff00ff00"
        val original = row("content://original", hash)
        val scaled = row("content://scaled", hash).copy(width = 1200, height = 800)
        assertTrue(potentialResizedDuplicate(original, scaled))
        assertFalse(potentialResizedDuplicate(original, original))
        assertFalse(potentialResizedDuplicate(original, scaled.copy(kind = "RAW", name = "frame.dng")))
        // Android MediaStore often classifies camera RAW as IMAGE, not RAW.
        assertFalse(potentialResizedDuplicate(original, scaled.copy(kind = "IMAGE", name = "frame.dng")))
        assertFalse(potentialResizedDuplicate(original.copy(name = "frame.iiq"), scaled))
        assertFalse(potentialResizedDuplicate(original, scaled.copy(recycled = true)))
        assertFalse(potentialResizedDuplicate(original, scaled.copy(name = "animated.gif")))
        assertFalse(potentialResizedDuplicate(original, scaled.copy(width = 1200, height = 700)))
        assertFalse(potentialResizedDuplicate(original, scaled.copy(dateTakenMillis = 1_004_000L)))
        assertFalse(potentialResizedDuplicate(original, scaled.copy(exifCamera = "Sony")))
        assertFalse(potentialResizedDuplicate(original, scaled.copy(perceptualHash = null)))
        assertFalse(potentialResizedDuplicate(original, scaled.copy(perceptualHash = "0000000000000000")))
    }

    @Test fun similarLuminanceDoesNotMeanPixelsAreEqual() {
        val original = row("content://original", "ffffffffffffffff")
        val colorEdited = row("content://color-edited", "ffffffffffffffff")
        // This is a *candidate* only. Color edits, retouching, and burst
        // frames must still be rejected by the future strict pixel verifier.
        assertTrue(potentialResizedDuplicate(original, colorEdited))
    }
    @Test fun hashComparisonHandlesUnsigned64BitAndMalformedInputs() {
        assertEquals(64, VisualDuplicateFingerprints.hammingDistance("0000000000000000", "ffffffffffffffff"))
        assertEquals(1, VisualDuplicateFingerprints.hammingDistance("8000000000000000", "0000000000000000"))
        assertEquals(0, VisualDuplicateFingerprints.hammingDistance("ABCDEF0123456789", "abcdef0123456789"))
        assertNull(VisualDuplicateFingerprints.hammingDistance("000000000000000", "0000000000000000"))
        assertNull(VisualDuplicateFingerprints.hammingDistance("00000000000000000", "0000000000000000"))
    }

    @Test fun cameraRawExtensionsAreExcludedEvenWhenMediaStoreReportsImage() {
        val jpeg = row("content://jpeg", "0123456789abcdef")
        for (extension in listOf("DNG", "CR2", "CR3", "NEF", "ARW", "RAF", "RW2", "3FR", "FFF", "IIQ")) {
            val raw = row("content://raw-$extension", "0123456789abcdef").copy(name = "frame.$extension")
            assertFalse("RAW + JPEG must not be merged: $extension", potentialResizedDuplicate(jpeg, raw))
            assertFalse("RAW + JPEG must not be merged: $extension", potentialResizedDuplicate(raw, jpeg))
        }
    }

    @Test fun visualCandidatesRejectRecycledAndBurstFramesRegardlessOfHash() {
        val original = row("content://a", "0123456789abcdef")
        val second = row("content://b", "0123456789abcdef")
        assertFalse(potentialResizedDuplicate(original.copy(recycled = true), second))
        assertFalse(potentialResizedDuplicate(original, second.copy(recycled = true)))
        assertFalse(potentialResizedDuplicate(original, second.copy(dateTakenMillis = 1_002_001L)))
        assertTrue(potentialResizedDuplicate(original, second.copy(dateTakenMillis = 1_002_000L)))
    }

    @Test fun crossPageResizeIsFoundWithoutComparingAllPairs() {
        val rows = (0 until 501).map { index ->
            row("content://image-$index", if (index == 0 || index == 500)
                "0123456789abcdef" else "ffffffffffffffff")
                .copy(dateTakenMillis = if (index == 0 || index == 500) 1_000_000L else 2_000_000L + index * 10_000L)
        }
        assertTrue(visualCandidatesAcrossPages(rows, 0).contains("content://image-0" to "content://image-500"))
        assertFalse(visualCandidatesAcrossPages(rows, 2).contains("content://image-0" to "content://image-500"))
    }

    @Test fun cropCandidateAcrossPagesDoesNotNeedMatchingHash() {
        val rows = (0 until 251).map { index ->
            row("content://image-$index", "0000000000000000")
                .copy(name = "unrelated-$index.jpg", dateTakenMillis = 10_000_000L + index * 10_000L)
        }.toMutableList()
        rows[0] = row("content://original", "0000000000000000")
            .copy(name = "frame.jpg", dateTakenMillis = 1_000_000L)
        rows[250] = row("content://crop", "ffffffffffffffff")
            .copy(name = "frame-crop.jpg", dateTakenMillis = 1_000_000L,
                width = 1600, height = 1200)
        assertTrue(visualCandidatesAcrossPages(rows, 0).contains("content://original" to "content://crop"))
    }

    @Test fun numberedEditorCropsAreShortlistedWithoutMatchingDHash() {
        val original = row("content://original", "0000000000000000")
            .copy(name = "DSC_12345.jpg")
        for (name in listOf("DSC_12345-crop-2.jpg", "DSC_12345_crop_3.png",
            "DSC_12345-cropped.jpg", "DSC_12345_cropped_4.jpg")) {
            val candidate = row("content://crop-$name", "ffffffffffffffff")
                .copy(name = name, width = 1600, height = 1200)
            assertEquals("dsc_12345", cropCandidateStem(name))
            assertTrue(name, potentialCropDuplicate(original, candidate))
            assertTrue(name, visualCandidatesAcrossPages(listOf(original, candidate), 0)
                .contains(original.uri to candidate.uri))
        }
        assertFalse(potentialCropDuplicate(original, original.copy(
            uri = "content://burst", name = "DSC_12346-crop.jpg",
            width = 1600, height = 1200)))
    }

    @Test fun crossPageCropWithoutExifTimestampIsStillReviewable() {
        val rows = (0 until 251).map { index ->
            row("content://other-$index", "aaaaaaaaaaaaaaaa")
                .copy(name = "other-$index.jpg", dateTakenMillis = 0L)
        }.toMutableList()
        rows[0] = row("content://original", "0000000000000000")
            .copy(name = "DSC_12345.jpg", dateTakenMillis = 0L)
        rows[250] = row("content://cropped", "ffffffffffffffff")
            .copy(name = "DSC_12345-crop.jpg", dateTakenMillis = 0L,
                width = 1600, height = 1200)
        assertTrue(potentialCropDuplicate(rows[0], rows[250]))
        assertTrue(visualCandidatesAcrossPages(rows, 0).contains(
            "content://original" to "content://cropped"))
        assertFalse(potentialCropDuplicate(rows[0],
            rows[250].copy(dateTakenMillis = 1_010_000L, name = "unrelated.jpg")))
    }

}

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
}

package com.mistermikhail.fgallery.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualDuplicateSourceVerificationTest {
    @Test fun sourceDecodeBoundsRejectUnknownOrOversizedImages() {
        assertFalse(safeSourceDimensions(0, 100))
        assertFalse(safeSourceDimensions(-1, 100))
        assertFalse(safeSourceDimensions(100, 0))
        assertFalse(safeSourceDimensions(5000, 5000))
        assertTrue(safeSourceDimensions(3000, 4000))
    }

    @Test fun sourceDecodeBoundsUseLongArithmetic() {
        assertFalse(safeSourceDimensions(Int.MAX_VALUE, Int.MAX_VALUE))
        assertTrue(safeSourceDimensions(1, 1))
        assertFalse(safeSourceDimensions(2, 2, 3))
    }

    @Test fun combinedDecodeBudgetRejectsTwoIndividuallyValidLargeImages() {
        assertTrue(safeSourceDimensions(3000, 3000))
        assertFalse(safeVisualPairDimensions(3000, 3000, 3000, 3000))
        assertTrue(safeVisualPairDimensions(2000, 2000, 2000, 2000))
        assertTrue(safeVisualPairDimensions(3000, 2000, 3000, 2000))
        assertFalse(safeVisualPairDimensions(3000, 2000, 3000, 2001))
    }

    @Test fun combinedDecodeBudgetFailsClosedForInvalidAndOverflowingDimensions() {
        assertFalse(safeVisualPairDimensions(0, 100, 100, 100))
        assertFalse(safeVisualPairDimensions(100, 100, -1, 100))
        assertFalse(safeVisualPairDimensions(Int.MAX_VALUE, Int.MAX_VALUE, 1, 1))
        assertFalse(safeVisualPairDimensions(1, 1, Int.MAX_VALUE, Int.MAX_VALUE))
        assertFalse(safeVisualPairDimensions(2, 2, 2, 2, 7))
    }

    private fun image(uri: String, name: String, width: Int, height: Int) = CatalogMediaRow(
        uri = uri, mediaId = 1, name = name, mimeType = "image/jpeg",
        kind = "IMAGE", dateTakenMillis = 123_000L, width = width, height = height,
        durationMillis = 0, album = "Camera", relativePath = "DCIM/",
        sizeBytes = 100, dateModifiedMillis = 123_000L,
        storageId = "phone", storageName = "Phone", folderTarget = "DCIM/",
        sourcePath = "/DCIM/$name", quickHash = null, fullHash = null,
        recycled = false,
    )

    @Test fun namedCropWithDifferentAspectRatioIsDiscoverableWithoutDHash() {
        val source = image("content://source", "IMG_12345.jpg", 4000, 3000)
        val crop = image("content://crop", "IMG_12345-crop.jpg", 2600, 2200)
        assertFalse(potentialResizedDuplicate(source, crop))
        assertTrue(potentialCropDuplicate(source, crop))
        assertTrue(potentialVisualDuplicate(source, crop))
        assertTrue(potentialVisualDuplicate(crop, source))
    }

    @Test fun cropDiscoveryRejectsBurstRawEditsAndUnrelatedFiles() {
        val source = image("content://source", "IMG_12345.jpg", 4000, 3000)
        val crop = image("content://crop", "IMG_12345-crop.jpg", 2600, 2200)
        assertFalse(potentialCropDuplicate(source, crop.copy(name = "IMG_12346-crop.jpg")))
        assertFalse(potentialCropDuplicate(source, crop.copy(dateTakenMillis = 130_000L)))
        assertFalse(potentialCropDuplicate(source, crop.copy(kind = "RAW", name = "IMG_12345.dng")))
        assertFalse(potentialCropDuplicate(source, crop.copy(width = 4100)))
        assertFalse(potentialCropDuplicate(source, crop.copy(width = 100, height = 100)))
        assertFalse(potentialCropDuplicate(source, crop.copy(uri = source.uri)))
        assertFalse(potentialCropDuplicate(source, crop.copy(recycled = true)))
    }

    @Test fun visualReviewPagesCoverItemsBeyondFirst250WithoutOverlap() {
        val all = (0 until 501).toList()
        assertTrue(visualCandidatePageCount(all.size) == 3)
        assertTrue(visualCandidatePage(all, 0).size == 250)
        assertTrue(visualCandidatePage(all, 1).first() == 250)
        assertTrue(visualCandidatePage(all, 2) == listOf(500))
        assertTrue((0 until 3).flatMap { visualCandidatePage(all, it) } == all)
    }

    @Test fun visualReviewPagingRejectsOutOfRangePagesAndExactBoundaries() {
        assertTrue(visualCandidatePageCount(500) == 2)
        assertTrue(visualCandidatePageCount(0) == 1)
        assertTrue(visualCandidatePage(emptyList<Int>(), 0).isEmpty())
        assertTrue(visualCandidatePage(listOf(1, 2), -1).isEmpty())
        assertTrue(visualCandidatePage(listOf(1, 2), 1).isEmpty())
    }
}

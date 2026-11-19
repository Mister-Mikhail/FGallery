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
}

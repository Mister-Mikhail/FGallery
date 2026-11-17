package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExifGpsCoordinatesTest {
    @Test fun validZeroAndSignedCoordinates() {
        assertEquals("0.000000, 0.000000", ExifGpsCoordinates.fromExif(doubleArrayOf(0.0, 0.0))?.display())
        assertEquals("-33.865143, 151.209900", ExifGpsCoordinates.fromExif(doubleArrayOf(-33.865143, 151.2099))?.display())
    }

    @Test fun rejectMissingInvalidAndNonFiniteCoordinates() {
        assertNull(ExifGpsCoordinates.fromExif(null))
        assertNull(ExifGpsCoordinates.fromExif(doubleArrayOf()))
        assertNull(ExifGpsCoordinates.fromExif(doubleArrayOf(1.0)))
        assertNull(ExifGpsCoordinates.fromExif(doubleArrayOf(91.0, 0.0)))
        assertNull(ExifGpsCoordinates.fromExif(doubleArrayOf(0.0, -181.0)))
        assertNull(ExifGpsCoordinates.fromExif(doubleArrayOf(Double.NaN, 0.0)))
        assertNull(ExifGpsCoordinates.fromExif(doubleArrayOf(0.0, Double.POSITIVE_INFINITY)))
        assertNotNull(ExifGpsCoordinates.fromExif(doubleArrayOf(-90.0, 180.0)))
    }
}

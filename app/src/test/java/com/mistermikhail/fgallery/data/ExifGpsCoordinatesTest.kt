package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExifGpsCoordinatesTest {
    @Test fun videoCoordinatesUseTheSourceIso6709Tag() {
        assertEquals("-35.120000, 149.150000", ExifGpsCoordinates.fromVideo("-35.12+149.15+12.0/")?.display())
        assertEquals("0.000000, 0.000000", ExifGpsCoordinates.fromVideo("+00.0000+000.0000/")?.display())
        assertNull(ExifGpsCoordinates.fromVideo("+91.00+001.00/"))
        assertNull(ExifGpsCoordinates.fromVideo("no GPS"))
    }
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

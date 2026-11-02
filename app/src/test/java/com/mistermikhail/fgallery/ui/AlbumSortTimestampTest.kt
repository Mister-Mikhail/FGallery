package com.mistermikhail.fgallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumSortTimestampTest {
    @Test fun modifiedFileOutranksMoreRecentlyCapturedFile() {
        val oldCaptureRecentlyEdited = listOf(
            albumFileTimestamp(capturedOrCreated = 100, modified = 900),
            albumFileTimestamp(capturedOrCreated = 200, modified = 300),
        ).maxOrNull()!!
        val newerCaptureNotEdited = listOf(
            albumFileTimestamp(capturedOrCreated = 800, modified = 0),
        ).maxOrNull()!!
        assertEquals(listOf("edited", "captured"),
            listOf("edited" to oldCaptureRecentlyEdited, "captured" to newerCaptureNotEdited)
                .sortedByDescending { it.second }.map { it.first })
        assertEquals(listOf("captured", "edited"),
            listOf("edited" to oldCaptureRecentlyEdited, "captured" to newerCaptureNotEdited)
                .sortedBy { it.second }.map { it.first })
    }

    @Test fun missingModifiedDateFallsBackToCreationDate() {
        assertEquals(1_000L, albumFileTimestamp(1_000L, 0L))
        assertEquals(2_000L, albumFileTimestamp(1_000L, 2_000L))
        assertEquals(0L, albumFileTimestamp(0L, 0L))
    }
}

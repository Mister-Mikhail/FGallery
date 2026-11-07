package com.mistermikhail.fgallery.data

import org.junit.Assert.*
import org.junit.Test

class CropCopyNamesTest {
    @Test fun copiesUseSequentialSuffixAndNeverRandomText() {
        val names = setOf("RAW_1.jpg", "RAW_2.jpg", "RAW_3.jpg")
        assertEquals("RAW_4.jpg", CropCopyNames.next("RAW.NEF", "jpg", names::contains))
        assertEquals("RAW_1.jpg", CropCopyNames.next("RAW.NEF", "jpg") { false })
        assertEquals("portrait.v2_2.jpg", CropCopyNames.next("portrait.v2.cr2", "jpg") { it == "portrait.v2_1.jpg" })
    }
}

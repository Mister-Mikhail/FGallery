package com.mistermikhail.fgallery.ui

import org.junit.Assert.*
import org.junit.Test

class RenameSelectionTest {
    @Test fun caretStartsAtExtensionEvenOnVeryLongFilenames() {
        val full = "2026_VeryLong_Photo_".repeat(20) + ".dng"
        assertEquals(full.lastIndexOf('.'), cursorBeforeExtension(full))
        assertEquals(5, cursorBeforeExtension("photo"))
    }

    @Test fun batchRenamingPreservesEachExtensionAndOriginalOrder() {
        assertEquals(
            listOf("Trip_1.CR2", "Trip_2.nef", "Trip_3.jpg", "Trip_4.mp4"),
            sequentialBatchNames("Trip", listOf("a.CR2", "b.nef", "c.jpg", "d.mp4")),
        )
        assertEquals(listOf("My.vacation_1.dng", "My.vacation_2.png"),
            sequentialBatchNames("My.vacation", listOf("a.dng", "b.png")))
    }

    @Test fun secondLongPressSelectsInclusiveRangeInDisplayOrder() {
        val all = listOf(6L, 7L, 8L, 9L, 10L, 11L, 12L)
        val first = inclusiveSelectionRange(all, null, 7L, emptySet())
        assertEquals(setOf(7L), first)
        val second = inclusiveSelectionRange(all, 7L, 11L, first)
        assertEquals(setOf(7L, 8L, 9L, 10L, 11L), second)
        val third = inclusiveSelectionRange(all, 11L, 6L, second)
        assertEquals(setOf(6L, 7L, 8L, 9L, 10L, 11L), third)
        assertEquals(setOf(22L), inclusiveSelectionRange(all, null, 22L, emptySet()))
    }
}

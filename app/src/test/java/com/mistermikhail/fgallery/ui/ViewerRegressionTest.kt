package com.mistermikhail.fgallery.ui

import org.junit.Assert.*
import org.junit.Test

class ViewerRegressionTest {
    @Test fun pinchRetainsOffCenterPointAndAddsPan() {
        val old = DocumentTransform(2f, -100f, 80f)
        val focusX = 260f; val focusY = 680f
        val sourceX = (focusX - 500f - old.x) / old.scale
        val sourceY = (focusY - 600f - old.y) / old.scale
        val next = transformDocument(old, focusX, focusY, 15f, -20f, 3f, 1000f, 1200f)
        assertEquals(focusX + 15f, 500f + next.x + sourceX * next.scale, .001f)
        assertEquals(focusY - 20f, 600f + next.y + sourceY * next.scale, .001f)
    }
    @Test fun returningToFitRemovesPan() {
        assertEquals(DocumentTransform(), transformDocument(DocumentTransform(7.2f, 800f, -900f), 20f, 30f, 0f, 0f, 1f, 1000f, 1200f))
    }
    @Test fun scrollingKeepsVisiblePlayerAndItsClock() {
        val rotation = LivePreviewRotation()
        assertNull(rotation.update(listOf(1L, 2L), false, 0))
        assertEquals(1L, rotation.update(listOf(1L, 2L), false, 700))
        assertEquals(1L, rotation.update(listOf(1L, 3L), true, 1000))
        assertEquals(1L, rotation.update(listOf(1L, 3L), false, 1100))
        assertEquals(1L, rotation.update(listOf(1L, 3L), false, 5600))
        assertEquals(3L, rotation.update(listOf(1L, 3L), false, 5700))
    }
    @Test fun leavingViewportEndsPreviewAndWaitsUntilScrollStops() {
        val rotation = LivePreviewRotation()
        rotation.update(listOf(1L), false, 0)
        rotation.update(listOf(1L), false, 700)
        assertNull(rotation.update(listOf(2L), true, 1000))
        assertNull(rotation.update(listOf(2L), false, 1100))
        assertEquals(2L, rotation.update(listOf(2L), false, 1800))
        assertEquals(2L, rotation.update(listOf(2L), false, 100000))
    }
}

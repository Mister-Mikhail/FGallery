package com.mistermikhail.fgallery.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualPixelScreenTest {
    private fun rgb(r: Int, g: Int, b: Int): Int =
        (255 shl 24) or (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun patterned(): IntArray = IntArray(VisualPixelScreen.PIXELS) { index ->
        val x = index % VisualPixelScreen.GRID
        val y = index / VisualPixelScreen.GRID
        rgb((x * 3 + y * 2 + x * y % 17) % 256,
            (x * 2 + y * 4 + x * y % 11) % 256,
            (x * 4 + y * 3 + x * y % 13) % 256)
    }

    private fun recolor(source: IntArray, delta: Int): IntArray = source.map { color ->
        rgb(((color ushr 16) and 255) + delta,
            ((color ushr 8) and 255) + delta,
            (color and 255) + delta)
    }.toIntArray()

    private fun crop(source: IntArray): IntArray =
        IntArray(VisualPixelScreen.PIXELS) { index ->
            val x = index % VisualPixelScreen.GRID
            val y = index / VisualPixelScreen.GRID
            val u = 0.25 + ((x + 0.5) / VisualPixelScreen.GRID) * 0.5
            val v = 0.25 + ((y + 0.5) / VisualPixelScreen.GRID) * 0.5
            val p = VisualPixelScreen.interpolate(source, u, v)
            rgb(p[0], p[1], p[2])
        }

    @Test fun resizesRequireMatchingColorNotJustSimilarLuminance() {
        val original = patterned()
        assertTrue(VisualPixelScreen.resizedCandidate(original, original.copyOf()))
        assertFalse(VisualPixelScreen.resizedCandidate(original, recolor(original, 30)))
        val retouched = original.copyOf()
        for (y in 18..29) for (x in 18..29) retouched[y * 48 + x] = rgb(255, 0, 0)
        assertFalse(VisualPixelScreen.resizedCandidate(original, retouched))
        assertFalse(VisualPixelScreen.resizedCandidate(IntArray(2304), IntArray(2304)))
    }

    @Test fun cropGeometryIsScreenedButGradingAndRetouchAreRejected() {
        val original = patterned()
        val cropped = crop(original)
        assertTrue(VisualPixelScreen.cropCandidate(original, cropped, 4000, 3000, 2000, 1500))
        assertFalse(VisualPixelScreen.cropCandidate(original, recolor(cropped, 30), 4000, 3000, 2000, 1500))
        val retouched = cropped.copyOf()
        for (y in 16..29) for (x in 16..29) retouched[y * 48 + x] = rgb(0, 255, 0)
        assertFalse(VisualPixelScreen.cropCandidate(original, retouched, 4000, 3000, 2000, 1500))
        assertFalse(VisualPixelScreen.cropCandidate(original, cropped, 0, 3000, 2000, 1500))
    }

    @Test fun cropSearchCanBeCancelled() {
        val original = patterned()
        var checked = 0
        try {
            VisualPixelScreen.cropCandidate(original, recolor(original, 60),
                4000, 3000, 2000, 1500) {
                checked++
                throw IllegalStateException("cancelled")
            }
            throw AssertionError("Expected cancellation")
        } catch (expected: IllegalStateException) {
            assertTrue(checked > 0)
        }
    }
    @Test fun cropSearchChecksCancellationInsideEachWidth() {
        val original = patterned()
        var checks = 0
        val found = VisualPixelScreen.cropCandidate(
            original, recolor(original, 60),
            4000, 3000, 2000, 1500,
        ) { checks++ }
        assertFalse(found)
        // Eleven candidate widths alone are insufficient for responsive
        // cancellation: each width can evaluate dozens of crop windows.
        assertTrue("Cancellation should be checked within each width: $checks", checks > 11)
    }

}

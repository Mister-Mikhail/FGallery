package com.mistermikhail.fgallery.data

import com.mistermikhail.fgallery.data.VisualDuplicatePixelVerification.Pixels
import com.mistermikhail.fgallery.data.VisualDuplicatePixelVerification.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualDuplicatePixelVerificationTest {
    private fun pixels(w: Int, h: Int, f: (Int, Int) -> Int) =
        Pixels(w, h, IntArray(w * h) { f(it % w, it / w) })

    @Test fun equalDecodedPixelsAreRecognizedButRetouchAndColorGradingAreNot() {
        val source = pixels(8, 8) { x, y -> 0xff000000.toInt() or (x * 101 + y * 19) }
        assertEquals(Result.EXACT_PIXELS, VisualDuplicatePixelVerification.verify(source, source.copy(argb = source.argb.copyOf())))
        val retouched = source.copy(argb = source.argb.copyOf().also { it[37] = it[37] xor 1 })
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(source, retouched))
        val graded = source.copy(argb = source.argb.map { it xor 0x00010000 }.toIntArray())
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(source, graded))
    }

    @Test fun onlyExactIntegerResizePassesAndChangedPixelFails() {
        val large = pixels(8, 8) { x, y -> 0xff000000.toInt() or ((x / 2) * 23 + (y / 2) * 71) }
        val small = pixels(4, 4) { x, y -> 0xff000000.toInt() or (x * 23 + y * 71) }
        assertEquals(Result.EXACT_INTEGER_RESIZE, VisualDuplicatePixelVerification.verify(large, small))
        assertEquals(Result.EXACT_INTEGER_RESIZE, VisualDuplicatePixelVerification.verify(small, large))
        val edited = small.copy(argb = small.argb.copyOf().also { it[4] = it[4] xor 0x100 })
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(large, edited))
    }

    @Test fun integerResizeRejectsChangesOutsideSampledBlockCorners() {
        val small = pixels(3, 3) { x, y -> 0xff000000.toInt() or (x * 51 + y * 7) }
        val enlarged = pixels(6, 6) { x, y -> small.argb[(y / 2) * 3 + x / 2] }
        assertEquals(Result.EXACT_INTEGER_RESIZE, VisualDuplicatePixelVerification.verify(enlarged, small))
        // Top-left samples of every 2x2 block remain unchanged. The former
        // implementation incorrectly accepted this edited enlargement.
        val edited = enlarged.copy(argb = enlarged.argb.copyOf().also { it[1] = it[1] xor 1 })
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(edited, small))
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(small, edited))
    }

    @Test fun exactCropRequiresEveryOriginalPixelAndRejectsBurstFrames() {
        val original = pixels(7, 6) { x, y -> 0xff000000.toInt() or (x * 31 + y * 73) }
        val crop = pixels(5, 4) { x, y -> original.argb[(y + 1) * 7 + x + 2] }
        assertEquals(Result.EXACT_CROP, VisualDuplicatePixelVerification.verify(original, crop))
        val burst = crop.copy(argb = crop.argb.copyOf().also { it[12] = it[12] xor 0x10000 })
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(original, burst))
    }

    @Test fun exactCropWorksBeyondOld4096PositionLimit() {
        val original = pixels(140, 120) { x, y ->
            0xff000000.toInt() or (x * 65537 + y * 131)
        }
        val crop = pixels(45, 36) { x, y ->
            original.argb[(y + 67) * original.width + x + 71]
        }
        assertEquals(Result.EXACT_CROP, VisualDuplicatePixelVerification.verify(original, crop))
        val retouched = crop.copy(argb = crop.argb.copyOf().also { it[100] = it[100] xor 1 })
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(original, retouched))
    }

    @Test fun lateCropSurvivesManyRepetitiveCornerAndCenterMatches() {
        // The genuine crop is near the end of the scan. Earlier positions
        // share its corner and centre pixels but not its interior details.
        // Previously 128 expensive false candidates exhausted the verifier.
        val original = pixels(48, 48) { _, _ -> 0xff000000.toInt() }
        val data = original.argb.copyOf()
        val left = 37
        val top = 37
        data[(top + 2) * 48 + left + 2] = 0xff123456.toInt()
        data[(top + 6) * 48 + left + 6] = 0xffabcdef.toInt()
        val detailed = Pixels(48, 48, data)
        val crop = pixels(8, 8) { x, y -> data[(top + y) * 48 + left + x] }
        assertEquals(Result.EXACT_CROP, VisualDuplicatePixelVerification.verify(detailed, crop))
        val retouched = crop.copy(argb = crop.argb.copyOf().also { it[2 * 8 + 2] = it[2 * 8 + 2] xor 1 })
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(detailed, retouched))
    }

    @Test fun lateCropWithDistinctEdgeMidpointSurvivesRepetitiveCandidates() {
        // The old five anchors all match the black background at hundreds of
        // earlier offsets. Only an edge midpoint distinguishes the late crop.
        val width = 48
        val data = IntArray(width * width) { 0xff000000.toInt() }
        val left = 37
        val top = 37
        data[(top + 4) * width + left] = 0xff12ab34.toInt()
        val original = Pixels(width, width, data)
        val crop = pixels(8, 8) { x, y -> data[(top + y) * width + left + x] }
        assertEquals(Result.EXACT_CROP, VisualDuplicatePixelVerification.verify(original, crop))
        val edited = crop.copy(argb = crop.argb.copyOf().also { it[4 * 8] = it[4 * 8] xor 1 })
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(original, edited))
    }

    @Test fun invalidAndExpensiveGeometryFailsClosed() {
        val original = pixels(100, 100) { x, y -> x + y }
        val small = pixels(2, 2) { x, y -> 10000 + x + y }
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(original, small))
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(Pixels(0, 1, intArrayOf()), small))
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(Pixels(2, 2, intArrayOf(1)), small))
    }

    @Test fun exifOrientationNormalizesRotationsAndMirrorsBeforeComparison() {
        val original = Pixels(3, 2, intArrayOf(1, 2, 3, 4, 5, 6))
        val expected = mapOf(
            1 to Pixels(3, 2, intArrayOf(1, 2, 3, 4, 5, 6)),
            2 to Pixels(3, 2, intArrayOf(3, 2, 1, 6, 5, 4)),
            3 to Pixels(3, 2, intArrayOf(6, 5, 4, 3, 2, 1)),
            4 to Pixels(3, 2, intArrayOf(4, 5, 6, 1, 2, 3)),
            5 to Pixels(2, 3, intArrayOf(1, 4, 2, 5, 3, 6)),
            6 to Pixels(2, 3, intArrayOf(4, 1, 5, 2, 6, 3)),
            7 to Pixels(2, 3, intArrayOf(6, 3, 5, 2, 4, 1)),
            8 to Pixels(2, 3, intArrayOf(3, 6, 2, 5, 1, 4)),
        )
        expected.forEach { (orientation, target) ->
            val normalized = orientSourcePixels(original, orientation)!!
            assertEquals(target.width, normalized.width)
            assertEquals(target.height, normalized.height)
            assertEquals(target.argb.toList(), normalized.argb.toList())
            assertEquals(Result.EXACT_PIXELS, VisualDuplicatePixelVerification.verify(normalized, target))
        }
        assertEquals(null, orientSourcePixels(original, 9))
    }
}

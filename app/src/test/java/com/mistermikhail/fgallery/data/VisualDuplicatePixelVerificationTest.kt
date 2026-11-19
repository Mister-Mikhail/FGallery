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

    @Test fun exactCropRequiresEveryOriginalPixelAndRejectsBurstFrames() {
        val original = pixels(7, 6) { x, y -> 0xff000000.toInt() or (x * 31 + y * 73) }
        val crop = pixels(5, 4) { x, y -> original.argb[(y + 1) * 7 + x + 2] }
        assertEquals(Result.EXACT_CROP, VisualDuplicatePixelVerification.verify(original, crop))
        val burst = crop.copy(argb = crop.argb.copyOf().also { it[12] = it[12] xor 0x10000 })
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(original, burst))
    }

    @Test fun invalidAndExpensiveGeometryFailsClosed() {
        val original = pixels(100, 100) { x, y -> x + y }
        val small = pixels(2, 2) { x, y -> x + y }
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(original, small))
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(Pixels(0, 1, intArrayOf()), small))
        assertEquals(Result.UNVERIFIED, VisualDuplicatePixelVerification.verify(Pixels(2, 2, intArrayOf(1)), small))
    }
}

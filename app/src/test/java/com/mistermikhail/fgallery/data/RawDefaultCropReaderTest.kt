package com.mistermikhail.fgallery.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RawDefaultCropReaderTest {
    @Test fun hasselbladPreviewIsNeverUsedAsSensorSize() {
        val bytes = makeTiff(orientation = 6)
        // Root IFD is a 3888 x 2918 preview. RAW SubIFD is 11904 x 8842
        // with DefaultCropSize = 11656 x 8742. Orientation 6 swaps axes.
        assertEquals(8742 to 11656, RawDefaultCropReader.parse(bytes))
    }

    @Test fun normalOrientationReturnsSensorCropUnswapped() {
        assertEquals(11656 to 8742, RawDefaultCropReader.parse(makeTiff(orientation = 1)))
    }

    @Test fun invalidOrOutOfBoundsCropFallsBackToLibRaw() {
        assertNull(RawDefaultCropReader.parse(makeTiff(orientation = 6, cropWidth = 11904)))
        assertNull(RawDefaultCropReader.parse(makeTiff(orientation = 6, cropWidth = 0)))
        assertNull(RawDefaultCropReader.parse(ByteArray(128)))
    }

    @Test fun truncatedOrInvalidSubIfdNeverCrashes() {
        assertNull(RawDefaultCropReader.parse(makeTiff(orientation = 6).copyOf(64)))
        val corrupted = makeTiff(orientation = 6).apply { putInt(48, Int.MAX_VALUE) }
        assertNull(RawDefaultCropReader.parse(corrupted))
    }

    private fun makeTiff(orientation: Int, cropWidth: Int = 11656): ByteArray {
        val bytes = ByteArray(1024)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.put(0, 0x49.toByte())
        b.put(1, 0x49.toByte())
        b.putShort(2, 42.toShort())
        b.putInt(4, 8)
        b.putShort(8, 4) // Root preview IFD (not the sensor).
        fun entry(offset: Int, id: Int, type: Int, count: Int, value: Int) {
            b.putShort(offset, id.toShort())
            b.putShort(offset + 2, type.toShort())
            b.putInt(offset + 4, count)
            b.putInt(offset + 8, value)
        }
        entry(10, 256, 4, 1, 3888)
        entry(22, 257, 4, 1, 2918)
        entry(34, 274, 3, 1, orientation)
        entry(46, 330, 4, 1, 160) // SubIFD points to actual sensor tags.
        b.putInt(58, 0)
        b.putShort(160, 4)
        entry(162, 256, 4, 1, 11904)
        entry(174, 257, 4, 1, 8842)
        entry(186, 50719, 3, 2, (96 shl 16) or 128)
        entry(198, 50720, 3, 2, (8742 shl 16) or cropWidth)
        b.putInt(210, 0)
        return bytes
    }

    private fun ByteArray.putInt(offset: Int, value: Int) {
        ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value)
    }
}

package com.mistermikhail.fgallery.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RawEmbeddedJpegTest {
    private fun tiff(compression: Int = 7, size: Int = 4, offset: Int = 128): File {
        val bytes = ByteArray(256)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0, 'I'.code.toByte()); buf.put(1, 'I'.code.toByte())
        buf.putShort(2, 42); buf.putInt(4, 8)
        buf.putShort(8, 4) // compression, strip offset, count, orientation
        fun entry(i: Int, tag: Int, type: Int, value: Int) {
            val pos = 10 + i * 12
            buf.putShort(pos, tag.toShort())
            buf.putShort(pos + 2, type.toShort())
            buf.putInt(pos + 4, 1)
            if (type == 3) buf.putShort(pos + 8, value.toShort())
            else buf.putInt(pos + 8, value)
        }
        entry(0, 0x0103, 3, compression)
        entry(1, 0x0111, 4, offset)
        entry(2, 0x0117, 4, size)
        entry(3, 0x0112, 3, 6)
        bytes[128] = 0xff.toByte(); bytes[129] = 0xd8.toByte()
        bytes[130] = 0xff.toByte(); bytes[131] = 0xd9.toByte()
        return File.createTempFile("fgallery-tiff", ".3fr").apply { writeBytes(bytes) }
    }

    @Test fun tiffJpegStripIsLocatedWithoutReadingFullRawBuffer() {
        val file = tiff()
        try {
            val entry = RandomAccessFile(file, "r").use { RawEmbeddedJpeg.locate(it.channel) }
            assertEquals(RawEmbeddedJpeg.Slice(128, 4, 6), entry)
        } finally { file.delete() }
    }

    @Test fun uncompressedOrOutOfBoundsOffsetsAreRejected() {
        for (file in listOf(tiff(compression = 1), tiff(size = 1000), tiff(offset = 254))) {
            try {
                assertNull(RandomAccessFile(file, "r").use { RawEmbeddedJpeg.locate(it.channel) })
            } finally { file.delete() }
        }
    }

    @Test fun dngPreviewInSecondIfdIsFoundWithoutRawDemosaic() {
        val bytes = ByteArray(512)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.put(0, 'I'.code.toByte()); b.put(1, 'I'.code.toByte())
        b.putShort(2, 42); b.putInt(4, 8)
        b.putShort(8, 1)
        b.putShort(10, 0x0112.toShort()) // IFD0 orientation
        b.putShort(12, 3)
        b.putInt(14, 1)
        b.putShort(18, 6)
        b.putInt(22, 64) // next IFD after first directory
        b.putShort(64, 2)
        fun entry(i: Int, tag: Int, value: Int) {
            val pos = 66 + i * 12
            b.putShort(pos, tag.toShort())
            b.putShort(pos + 2, 4)
            b.putInt(pos + 4, 1)
            b.putInt(pos + 8, value)
        }
        entry(0, 0x0201, 200)
        entry(1, 0x0202, 100)
        bytes[200] = 0xff.toByte(); bytes[201] = 0xd8.toByte()
        bytes[298] = 0xff.toByte(); bytes[299] = 0xd9.toByte()
        val file = File.createTempFile("fgallery-dng-ifd1", ".dng")
        try {
            file.writeBytes(bytes)
            val slice = RandomAccessFile(file, "r").use { RawEmbeddedJpeg.locate(it.channel) }
            assertEquals(RawEmbeddedJpeg.Slice(200, 100, 6), slice)
        } finally { file.delete() }
    }

    @Test fun largestValidJpegWinsAndNonJpegOffsetsAreIgnored() {
        val bytes = ByteArray(512)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.put(0, 'I'.code.toByte()); b.put(1, 'I'.code.toByte())
        b.putShort(2, 42); b.putInt(4, 8)
        b.putShort(8, 2)
        fun entry(at: Int, tag: Int, value: Int) {
            b.putShort(at, tag.toShort()); b.putShort(at + 2, 4)
            b.putInt(at + 4, 1); b.putInt(at + 8, value)
        }
        entry(10, 0x0201, 150)
        entry(22, 0x0202, 8)
        b.putInt(34, 64)
        b.putShort(64, 2)
        entry(66, 0x0201, 200)
        entry(78, 0x0202, 90)
        bytes[150] = 0xff.toByte(); bytes[151] = 0xd8.toByte()
        bytes[200] = 0xff.toByte(); bytes[201] = 0xd8.toByte()
        val file = File.createTempFile("fgallery-dng-largest", ".dng")
        try {
            file.writeBytes(bytes)
            assertEquals(RawEmbeddedJpeg.Slice(200, 90, 1),
                RandomAccessFile(file, "r").use { RawEmbeddedJpeg.locate(it.channel) })
            RandomAccessFile(file, "rw").use { it.seek(200); it.writeByte(0) }
            assertEquals(RawEmbeddedJpeg.Slice(150, 8, 1),
                RandomAccessFile(file, "r").use { RawEmbeddedJpeg.locate(it.channel) })
        } finally { file.delete() }
    }

    @Test fun unrecognisedHeaderDoesNotCountAsEmbeddedJpeg() {
        val file = tiff()
        try {
            RandomAccessFile(file, "rw").use { it.seek(0); it.writeByte(0) }
            assertNull(RandomAccessFile(file, "r").use { RawEmbeddedJpeg.locate(it.channel) })
        } finally { file.delete() }
    }
}

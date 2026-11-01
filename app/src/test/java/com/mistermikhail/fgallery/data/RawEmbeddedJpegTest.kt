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

    @Test fun unrecognisedHeaderDoesNotCountAsEmbeddedJpeg() {
        val file = tiff()
        try {
            RandomAccessFile(file, "rw").use { it.seek(0); it.writeByte(0) }
            assertNull(RandomAccessFile(file, "r").use { RawEmbeddedJpeg.locate(it.channel) })
        } finally { file.delete() }
    }
}

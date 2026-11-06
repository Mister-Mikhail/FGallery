package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class AviCodecInfoTest {
    private fun sample(codec: String, streamType: String = "vids"): ByteArray {
        val bytes = ByteArray(100)
        fun put(offset: Int, text: String) {
            text.toByteArray(Charsets.US_ASCII).copyInto(bytes, offset)
        }
        put(0, "RIFF")
        put(8, "AVI ")
        put(12, "LIST")
        put(24, "strh")
        bytes[28] = 56
        put(32, streamType)
        put(36, codec)
        return bytes
    }

    @Test fun detectsMjpegAviCodec() {
        assertEquals("MJPG", AviCodecInfo.fourCc(ByteArrayInputStream(sample("MJPG"))))
    }

    @Test fun detectsOtherAviCodecWithoutPromisingPlayback() {
        assertEquals("XVID", AviCodecInfo.fourCc(ByteArrayInputStream(sample("XVID"))))
    }

    @Test fun ignoresAudioStream() {
        assertNull(AviCodecInfo.fourCc(ByteArrayInputStream(sample("MJPG", "auds"))))
    }

    @Test fun rejectsNonAviAndTruncatedHeaders() {
        assertNull(AviCodecInfo.fourCc(ByteArrayInputStream(byteArrayOf(1, 2, 3))))
        assertNull(AviCodecInfo.fourCc(ByteArrayInputStream(ByteArray(128))))
    }
}

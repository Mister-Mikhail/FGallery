package com.mistermikhail.fgallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class AviCodecInfoTest {
    private fun chunk(tag: String, payload: ByteArray): ByteArray {
        val size = payload.size
        return tag.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(size.toByte(), (size ushr 8).toByte(), (size ushr 16).toByte(), (size ushr 24).toByte()) +
            payload + if (size % 2 != 0) byteArrayOf(0) else byteArrayOf()
    }

    private fun list(type: String, vararg children: ByteArray): ByteArray =
        chunk("LIST", type.toByteArray(Charsets.US_ASCII) + children.fold(byteArrayOf()) { a, b -> a + b })

    private fun sample(codec: String, streamType: String = "vids"): ByteArray {
        val streamHeader = ByteArray(56)
        streamType.toByteArray(Charsets.US_ASCII).copyInto(streamHeader, 0)
        codec.toByteArray(Charsets.US_ASCII).copyInto(streamHeader, 4)
        val body = "AVI ".toByteArray(Charsets.US_ASCII) +
            list("hdrl", list("strl", chunk("strh", streamHeader)))
        return chunk("RIFF", body)
    }

    @Test fun detectsMjpegAviCodec() {
        assertEquals("MJPG", AviCodecInfo.fourCc(ByteArrayInputStream(sample("MJPG"))))
    }

    @Test fun detectsOtherAviCodecWithoutPromisingPlayback() {
        assertEquals("XVID", AviCodecInfo.fourCc(ByteArrayInputStream(sample("XVID"))))
    }

    @Test fun fallsBackToBitmapInfoCompressionWhenVideoHandlerIsBlank() {
        val streamHeader = ByteArray(56)
        "vids".toByteArray(Charsets.US_ASCII).copyInto(streamHeader, 0)
        val bitmapInfo = ByteArray(40)
        bitmapInfo[0] = 40 // biSize
        "MJPG".toByteArray(Charsets.US_ASCII).copyInto(bitmapInfo, 16)
        val body = "AVI ".toByteArray(Charsets.US_ASCII) +
            list("hdrl", list("strl", chunk("strh", streamHeader), chunk("strf", bitmapInfo)))
        assertEquals("MJPG", AviCodecInfo.fourCc(ByteArrayInputStream(chunk("RIFF", body))))
    }

    @Test fun ignoresBitmapInfoCompressionForAudioStreams() {
        val streamHeader = ByteArray(56)
        "auds".toByteArray(Charsets.US_ASCII).copyInto(streamHeader, 0)
        val bitmapInfo = ByteArray(40)
        bitmapInfo[0] = 40
        "MJPG".toByteArray(Charsets.US_ASCII).copyInto(bitmapInfo, 16)
        val body = "AVI ".toByteArray(Charsets.US_ASCII) +
            list("hdrl", list("strl", chunk("strh", streamHeader), chunk("strf", bitmapInfo)))
        assertNull(AviCodecInfo.fourCc(ByteArrayInputStream(chunk("RIFF", body))))
    }

    @Test fun ignoresAudioStream() {
        assertNull(AviCodecInfo.fourCc(ByteArrayInputStream(sample("MJPG", "auds"))))
    }

    @Test fun ignoresFakeHeaderInsideFramePayload() {
        val fake = chunk("strh", ByteArray(56).also {
            "vidsMJPG".toByteArray(Charsets.US_ASCII).copyInto(it)
        })
        val body = "AVI ".toByteArray(Charsets.US_ASCII) + list("movi", fake)
        assertNull(AviCodecInfo.fourCc(ByteArrayInputStream(chunk("RIFF", body))))
    }

    @Test fun rejectsMalformedRiffChunkLengths() {
        val malformed = sample("MJPG").clone()
        // LIST claims to extend beyond the RIFF container.
        malformed[16] = 0x7f
        assertNull(AviCodecInfo.fourCc(ByteArrayInputStream(malformed)))
    }

    @Test fun rejectsNonAviAndTruncatedHeaders() {
        assertNull(AviCodecInfo.fourCc(ByteArrayInputStream(byteArrayOf(1, 2, 3))))
        assertNull(AviCodecInfo.fourCc(ByteArrayInputStream(ByteArray(128))))
    }
}

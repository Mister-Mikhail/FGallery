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

    @Test fun describesCommonCodecFamiliesWithoutClaimingDecoderSupport() {
        assertEquals("Motion JPEG (MJPEG)", AviCodecInfo.codecDescription("mjpg"))
        assertEquals("MPEG-4 Part 2 (DivX/Xvid)", AviCodecInfo.codecDescription("XVID"))
        assertEquals("H.264/AVC", AviCodecInfo.codecDescription("h264"))
        assertEquals("H.265/HEVC", AviCodecInfo.codecDescription("HEVC"))
        assertEquals("неизвестный или нестандартный видеокодек", AviCodecInfo.codecDescription("ZZZZ"))
    }

    @Test fun recognizesSpacePaddedUncompressedAviFourCc() {
        assertEquals("DIB ", AviCodecInfo.fourCc(ByteArrayInputStream(sample("DIB "))))
        assertEquals("несжатое RGB-видео (AVI)", AviCodecInfo.codecDescription("DIB "))
        assertEquals("несжатое YUV-видео (AVI)", AviCodecInfo.codecDescription("YUY2"))
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

    @Test fun recognizesNumericUncompressedRgbBitmapInfoCompression() {
        for (compression in listOf(0, 3)) {
            val streamHeader = ByteArray(56)
            "vids".toByteArray(Charsets.US_ASCII).copyInto(streamHeader)
            val bitmapInfo = ByteArray(40)
            bitmapInfo[0] = 40
            bitmapInfo[16] = compression.toByte()
            val body = "AVI ".toByteArray(Charsets.US_ASCII) +
                list("hdrl", list("strl", chunk("strh", streamHeader), chunk("strf", bitmapInfo)))
            assertEquals("DIB ", AviCodecInfo.fourCc(ByteArrayInputStream(chunk("RIFF", body))))
        }
    }

    @Test fun rejectsTruncatedBitmapInfoHeaderEvenWhenCompressionBytesExist() {
        val streamHeader = ByteArray(56)
        "vids".toByteArray(Charsets.US_ASCII).copyInto(streamHeader)
        val bitmapInfo = ByteArray(20)
        bitmapInfo[0] = 40 // biSize claims 40 bytes, but strf contains only 20
        "MJPG".toByteArray(Charsets.US_ASCII).copyInto(bitmapInfo, 16)
        val body = "AVI ".toByteArray(Charsets.US_ASCII) +
            list("hdrl", list("strl", chunk("strh", streamHeader), chunk("strf", bitmapInfo)))
        assertNull(AviCodecInfo.fourCc(ByteArrayInputStream(chunk("RIFF", body))))
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


    @Test fun selectsVideoStreamAfterAudioWithBlankHandlerAndOddChunkPadding() {
        val audioHeader = ByteArray(56)
        "auds".toByteArray(Charsets.US_ASCII).copyInto(audioHeader)
        val videoHeader = ByteArray(56)
        "vids".toByteArray(Charsets.US_ASCII).copyInto(videoHeader)
        val bitmapInfo = ByteArray(40)
        bitmapInfo[0] = 40
        "XVID".toByteArray(Charsets.US_ASCII).copyInto(bitmapInfo, 16)
        val body = "AVI ".toByteArray(Charsets.US_ASCII) + list(
            "hdrl",
            list("strl", chunk("strh", audioHeader), chunk("strf", byteArrayOf(1, 2, 3))),
            list("strl", chunk("JUNK", byteArrayOf(7)), chunk("strh", videoHeader), chunk("strf", bitmapInfo)),
        )
        assertEquals("XVID", AviCodecInfo.fourCc(ByteArrayInputStream(chunk("RIFF", body))))
    }

    @Test fun truncatedAviNeverReportsCodecFromIncompleteVideoHeader() {
        val complete = sample("MJPG")
        assertNull(AviCodecInfo.fourCc(ByteArrayInputStream(complete.copyOf(complete.size - 1))))
    }

    @Test fun readsVideoHeaderFromLargeAviWithoutLoadingFullVideo() {
        val videoHeader = ByteArray(56)
        "vidsMJPG".toByteArray(Charsets.US_ASCII).copyInto(videoHeader)
        val header = "AVI ".toByteArray(Charsets.US_ASCII) +
            list("hdrl", list("strl", chunk("strh", videoHeader)))
        // Large AVI containers are sampled to 256 KiB. The header must remain
        // discoverable even when the declared RIFF size exceeds that limit.
        val riff = chunk("RIFF", header).clone()
        val declaredSize = 8 * 1024 * 1024
        for (index in 0..3) riff[4 + index] = (declaredSize ushr (8 * index)).toByte()
        assertEquals("MJPG", AviCodecInfo.fourCc(ByteArrayInputStream(riff)))
    }


    @Test fun largeSafAviStopsReadingAfterCompleteHeader() {
        val header = sample("MJPG")
        val declaredSize = 8 * 1024 * 1024
        for (index in 0..3) header[4 + index] = (declaredSize ushr (8 * index)).toByte()
        val source = header + ByteArray(256 * 1024)
        val stream = object : ByteArrayInputStream(source) {
            var bytesRead = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, minOf(len, 7))
                if (n > 0) bytesRead += n
                return n
            }
            override fun read(): Int {
                val n = super.read()
                if (n >= 0) bytesRead++
                return n
            }
        }
        assertEquals("MJPG", AviCodecInfo.fourCc(stream))
        org.junit.Assert.assertTrue("Should not drain 256 KiB from SAF", stream.bytesRead < 8192)
    }

    @Test fun largeAviFindsCompleteVideoHeaderInsidePartiallyBufferedHdrl() {
        val videoHeader = ByteArray(56)
        "vidsMJPG".toByteArray(Charsets.US_ASCII).copyInto(videoHeader)
        val riff = chunk(
            "RIFF",
            "AVI ".toByteArray(Charsets.US_ASCII) +
                list("hdrl", list("strl", chunk("strh", videoHeader)))
        )
        // A real AVI may have a very large hdrl LIST with later chunks that
        // have not yet arrived from SAF. Both declared boundaries remain valid.
        val large = riff + ByteArray(256 * 1024)
        val riffSize = 8 * 1024 * 1024
        val hdrlSize = 1024 * 1024
        for (i in 0..3) {
            large[4 + i] = (riffSize ushr (8 * i)).toByte()
            large[16 + i] = (hdrlSize ushr (8 * i)).toByte()
        }
        val stream = object : ByteArrayInputStream(large) {
            var bytesRead = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, minOf(len, 11))
                if (n > 0) bytesRead += n
                return n
            }
        }
        assertEquals("MJPG", AviCodecInfo.fourCc(stream))
        org.junit.Assert.assertTrue(stream.bytesRead < 8192)
    }

    @Test fun rejectsNonAviAndTruncatedHeaders() {
        assertNull(AviCodecInfo.fourCc(ByteArrayInputStream(byteArrayOf(1, 2, 3))))
        assertNull(AviCodecInfo.fourCc(ByteArrayInputStream(ByteArray(128))))
    }
}

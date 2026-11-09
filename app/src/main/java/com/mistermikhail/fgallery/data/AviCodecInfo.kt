package com.mistermikhail.fgallery.data

import java.io.InputStream
import java.util.Locale

/**
 * Best-effort AVI video FOURCC for diagnostics, not a claim of decoder support.
 * Walk RIFF/LIST chunk boundaries rather than scanning compressed frame bytes for
 * an accidental "strh" signature. The input is capped to 256 KiB.
 */
internal object AviCodecInfo {
    /** Human-readable hint only: decoder availability must be checked separately. */
    fun codecDescription(fourCc: String): String = when (fourCc.uppercase(Locale.ROOT)) {
        "MJPG", "JPEG" -> "Motion JPEG (MJPEG)"
        "XVID", "DIVX", "DX50" -> "MPEG-4 Part 2 (DivX/Xvid)"
        "H264", "X264", "AVC1" -> "H.264/AVC"
        "HEVC", "H265", "X265" -> "H.265/HEVC"
        "HFYU" -> "HuffYUV"
        "FFV1" -> "FFV1"
        "DIB ", "RGB " -> "несжатое RGB-видео (AVI)"
        "YUY2", "UYVY" -> "несжатое YUV-видео (AVI)"
        else -> "неизвестный или нестандартный видеокодек"
    }

    fun fourCc(input: InputStream): String? {
        val bytes = ByteArray(256 * 1024)
        var count = 0
        fun readUntil(target: Int) {
            while (count < target) {
                val n = input.read(bytes, count, target - count)
                if (n < 0) break
                if (n == 0) {
                    val one = input.read()
                    if (one < 0) break
                    bytes[count++] = one.toByte()
                } else count += n
            }
        }

        readUntil(12)
        if (count < 12 || ascii(bytes, 0, 4) != "RIFF" || ascii(bytes, 8, 4) != "AVI ") return null
        val riffSize = uint32(bytes, 4)
        if (riffSize < 4L) return null
        val declaredEnd = riffSize + 8L
        val limit = minOf(bytes.size.toLong(), declaredEnd).toInt()

        // Small RIFF containers must be complete, even if the header looks valid.
        if (declaredEnd <= bytes.size) {
            readUntil(limit)
            if (count < limit) return null
            return findVideoHeader(bytes, 12, limit, insideStream = false, parentEnd = declaredEnd)
        }

        // Large SAF-backed AVI: stop reading as soon as a complete video
        // header is available. Never scan more than the original 256 KiB cap.
        while (count < limit) {
            val target = minOf(limit, count + 4096)
            readUntil(target)
            findVideoHeader(bytes, 12, count, insideStream = false, parentEnd = declaredEnd)?.let { return it }
            if (count < target) break
        }
        return null
    }

    /**
     * [end] is the available prefix, while [parentEnd] is the declared RIFF/LIST
     * boundary. Large SAF streams may have complete strh/strf chunks inside an
     * incomplete hdrl LIST. Never inspect an incomplete leaf chunk.
     */
    private fun findVideoHeader(
        bytes: ByteArray,
        start: Int,
        end: Int,
        insideStream: Boolean,
        parentEnd: Long,
    ): String? {
        var offset = start
        var videoStream = false
        var formatCompression: String? = null
        while (offset.toLong() + 8L <= end.toLong()) {
            val size = uint32(bytes, offset + 4)
            val dataStart = offset + 8
            val dataEnd = dataStart.toLong() + size
            val next = dataEnd + (size and 1L)
            if (dataEnd < dataStart || next > parentEnd || next <= offset) return null
            when (ascii(bytes, offset, 4)) {
                "LIST" -> {
                    if (size < 4L || dataStart.toLong() + 4L > end) return null
                    val listType = ascii(bytes, dataStart, 4)
                    if (listType == "hdrl" || listType == "strl") {
                        findVideoHeader(
                            bytes, dataStart + 4, minOf(dataEnd, end.toLong()).toInt(),
                            listType == "strl", dataEnd,
                        )?.let { return it }
                    }
                }
                "strh" -> {
                    if (dataEnd > end) return null
                    if (insideStream && size >= 56L && ascii(bytes, dataStart, 4) == "vids") {
                        videoStream = true
                        val codec = ascii(bytes, dataStart + 4, 4).uppercase(Locale.ROOT)
                        if (codec.length == 4 && codec.all { it.code in 32..126 } && codec.any { it != ' ' }) return codec
                    }
                }
                "strf" -> {
                    if (dataEnd > end) return null
                    if (insideStream && videoStream && size >= 20L) {
                        val headerSize = uint32(bytes, dataStart)
                        if (headerSize >= 20L && headerSize <= size) {
                            val compression = uint32(bytes, dataStart + 16)
                            if (compression == 0L || compression == 3L) {
                                formatCompression = "DIB "
                            } else {
                                val codec = ascii(bytes, dataStart + 16, 4).uppercase(Locale.ROOT)
                                if (codec.length == 4 && codec.all { it.code in 32..126 } && codec.any { it != ' ' }) {
                                    formatCompression = codec
                                }
                            }
                        }
                    }
                }
                else -> if (dataEnd > end) return null
            }
            if (next > end) break
            offset = next.toInt()
        }
        return if (insideStream && videoStream) formatCompression else null
    }

    private fun uint32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 255L) or
            ((bytes[offset + 1].toLong() and 255L) shl 8) or
            ((bytes[offset + 2].toLong() and 255L) shl 16) or
            ((bytes[offset + 3].toLong() and 255L) shl 24)

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.US_ASCII)
}

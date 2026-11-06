package com.mistermikhail.fgallery.data

import java.io.InputStream
import java.util.Locale

/**
 * Best-effort AVI video FOURCC for diagnostics, not a claim of decoder support.
 * Walk RIFF/LIST chunk boundaries rather than scanning compressed frame bytes for
 * an accidental "strh" signature. The input is capped to 256 KiB.
 */
internal object AviCodecInfo {
    fun fourCc(input: InputStream): String? {
        val bytes = ByteArray(256 * 1024)
        var count = 0
        while (count < bytes.size) {
            val n = input.read(bytes, count, bytes.size - count)
            if (n < 0) break
            if (n == 0) {
                val one = input.read()
                if (one < 0) break
                bytes[count++] = one.toByte()
            } else count += n
        }
        if (count < 12 || ascii(bytes, 0, 4) != "RIFF" || ascii(bytes, 8, 4) != "AVI ") return null
        val riffSize = uint32(bytes, 4)
        if (riffSize < 4L) return null
        return findVideoHeader(bytes, 12, minOf(count.toLong(), riffSize + 8).toInt(), insideStream = false)
    }

    private fun findVideoHeader(bytes: ByteArray, start: Int, end: Int, insideStream: Boolean): String? {
        var offset = start
        while (offset.toLong() + 8 <= end.toLong()) {
            val size = uint32(bytes, offset + 4)
            val dataStart = offset + 8
            val dataEnd = dataStart.toLong() + size
            if (dataEnd > end || dataEnd < dataStart) return null
            when (ascii(bytes, offset, 4)) {
                "LIST" -> if (size >= 4L) {
                    val listType = ascii(bytes, dataStart, 4)
                    // Only 'strl' contains a stream header. 'hdrl' contains stream lists.
                    if (listType == "hdrl" || listType == "strl") {
                        findVideoHeader(bytes, dataStart + 4, dataEnd.toInt(), listType == "strl")
                            ?.let { return it }
                    }
                }
                "strh" -> if (insideStream && size >= 56L &&
                    ascii(bytes, dataStart, 4) == "vids") {
                    val codec = ascii(bytes, dataStart + 4, 4).trim().uppercase(Locale.ROOT)
                    if (codec.length == 4 && codec.all { it.code in 33..126 }) return codec
                }
            }
            val next = dataEnd + (size and 1L)
            if (next <= offset || next > Int.MAX_VALUE) return null
            offset = next.toInt()
        }
        return null
    }

    private fun uint32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 255L) or
            ((bytes[offset + 1].toLong() and 255L) shl 8) or
            ((bytes[offset + 2].toLong() and 255L) shl 16) or
            ((bytes[offset + 3].toLong() and 255L) shl 24)

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.US_ASCII)
}

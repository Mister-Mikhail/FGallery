package com.mistermikhail.fgallery.data

import java.io.InputStream
import java.util.Locale

/**
 * Reads only AVI RIFF headers, never video frames. Returns a best-effort FOURCC for
 * actionable playback errors when Media3 cannot parse/decode the container.
 */
internal object AviCodecInfo {
    fun fourCc(input: InputStream): String? {
        val bytes = ByteArray(256 * 1024)
        var count = 0
        while (count < bytes.size) {
            val read = input.read(bytes, count, bytes.size - count)
            if (read <= 0) break
            count += read
        }
        if (count < 12 || ascii(bytes, 0, 4) != "RIFF" || ascii(bytes, 8, 4) != "AVI ") return null
        // AVI stream headers: 'strh' + 56-byte AVISTREAMHEADER. fccType at +8,
        // fccHandler (codec FOURCC) at +12. Audio streams must be ignored.
        for (i in 12 until count - 16) {
            if (ascii(bytes, i, 4) != "strh") continue
            val size = (bytes[i + 4].toInt() and 255) or
                ((bytes[i + 5].toInt() and 255) shl 8) or
                ((bytes[i + 6].toInt() and 255) shl 16) or
                ((bytes[i + 7].toInt() and 255) shl 24)
            if (size < 8 || size > count - i - 8 || ascii(bytes, i + 8, 4) != "vids") continue
            val codec = ascii(bytes, i + 12, 4).trim().uppercase(Locale.ROOT)
            if (codec.length == 4 && codec.all { it.code in 33..126 }) return codec
        }
        return null
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.US_ASCII)
}

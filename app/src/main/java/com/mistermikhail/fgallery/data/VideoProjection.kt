package com.mistermikhail.fgallery.data

import android.content.Context
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream

/** Read projection metadata, without treating ordinary 2:1 frames or filenames as spherical. */
object VideoProjection {
    fun isSpherical(context: Context, item: MediaItem): Boolean = runCatching {
        context.contentResolver.openAssetFileDescriptor(item.uri, "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                val channel = input.channel
                val end = if (descriptor.declaredLength >= 0) descriptor.startOffset + descriptor.declaredLength else channel.size()
                containsProjection(channel, descriptor.startOffset, end)
            }
        } ?: false
    }.getOrDefault(false)

    internal fun containsProjection(channel: FileChannel, start: Long = 0, end: Long = channel.size()): Boolean {
        var boxes = 0
        fun bytes(offset: Long, count: Int): ByteArray? {
            if (offset < start || offset + count > end) return null
            val buffer = ByteBuffer.allocate(count)
            while (buffer.hasRemaining()) {
                val read = channel.read(buffer, offset + buffer.position())
                if (read <= 0) return null
            }
            return buffer.array()
        }
        fun scan(from: Long, until: Long, depth: Int): Boolean {
            if (depth > 12) return false
            var offset = from
            while (offset <= until - 8 && ++boxes <= 10000) {
                val header = bytes(offset, 8) ?: return false
                val buffer = ByteBuffer.wrap(header)
                var size = buffer.int.toLong() and 0xffffffffL
                val type = String(header, 4, 4, Charsets.US_ASCII)
                var headerSize = 8L
                if (size == 1L) { size = bytes(offset + 8, 8)?.let { ByteBuffer.wrap(it).long } ?: return false; headerSize = 16L }
                if (size == 0L) size = until - offset
                if (size < headerSize || size > until - offset) return false
                val payload = offset + headerSize
                if (type == "sv3d" || type == "proj") return true
                if (type == "uuid" && size - headerSize in 16L..65536L) {
                    val text = bytes(payload, (size - headerSize).toInt())?.toString(Charsets.UTF_8).orEmpty()
                    if (Regex("(?:[A-Za-z0-9_]+:)?Spherical\\s*>\\s*true\\s*<", RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
                }
                val childOffset = when (type) {
                    "moov", "trak", "mdia", "minf", "stbl" -> payload
                    "stsd" -> payload + 8
                    "avc1", "avc3", "hev1", "hvc1", "vp09", "av01", "encv" -> payload + 78
                    else -> null
                }
                if (childOffset != null && childOffset < offset + size && scan(childOffset, offset + size, depth + 1)) return true
                offset += size
            }
            return false
        }
        return scan(start, end, 0)
    }
}

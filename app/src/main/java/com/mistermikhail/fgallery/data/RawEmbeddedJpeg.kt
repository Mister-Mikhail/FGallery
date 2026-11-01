package com.mistermikhail.fgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Read-only, bounded fast path for a JPEG stored in a classic TIFF/RAW IFD0.
 *
 * Most camera RAW containers are TIFF-based, but not all provide a complete
 * JPEG in IFD0. Missing/invalid preview MUST return null, preserving LibRaw.
 * Never read or modify the RAW image data, nor assume IFD0 is full resolution.
 */
internal object RawEmbeddedJpeg {
    data class Slice(val offset: Long, val length: Int, val orientation: Int)

    fun preview(context: Context, item: MediaItem, edge: Int): Bitmap? = runCatching {
        val direct = StorageAccess.file(context, item)?.takeIf { it.isFile && it.canRead() }
        if (direct != null) {
            RandomAccessFile(direct, "r").use { readPreview(it.channel, edge) }
        } else {
            context.contentResolver.openFileDescriptor(item.uri, "r")?.use { fd ->
                FileInputStream(fd.fileDescriptor).use { readPreview(it.channel, edge) }
            }
        }
    }.getOrNull()

    private fun readPreview(channel: FileChannel, edge: Int): Bitmap? {
        val slice = locate(channel) ?: return null
        val bytes = read(channel, slice.offset, slice.length) ?: return null
        if ((bytes[0].toInt() and 255) != 255 || (bytes[1].toInt() and 255) != 216)
            return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > edge)
            options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val degrees = when (slice.orientation) { 3 -> 180f; 6 -> 90f; 8 -> -90f; else -> 0f }
        if (degrees == 0f) return bitmap
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(degrees) }, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } catch (e: Exception) {
            bitmap.recycle()
            throw e
        }
    }

    /** Pure TIFF range parser. Sizes and offsets are always checked before I/O. */
    internal fun locate(channel: FileChannel): Slice? {
        val length = channel.size()
        if (length < 16) return null
        val header = read(channel, 0, 8) ?: return null
        val order = when {
            header[0] == 0x49.toByte() && header[1] == 0x49.toByte() -> ByteOrder.LITTLE_ENDIAN
            header[0] == 0x4d.toByte() && header[1] == 0x4d.toByte() -> ByteOrder.BIG_ENDIAN
            else -> return null
        }
        val top = ByteBuffer.wrap(header).order(order)
        if (top.getShort(2).toInt() and 0xffff != 42) return null
        val ifd = unsignedInt(top.getInt(4))
        if (ifd > length - 2) return null
        val countBytes = read(channel, ifd, 2) ?: return null
        val count = ByteBuffer.wrap(countBytes).order(order).short.toInt() and 0xffff
        if (count > 4_096 || ifd + 2L + count * 12L > length) return null
        val entries = read(channel, ifd + 2L, count * 12) ?: return null

        var jpegStart: Long? = null
        var jpegSize: Long? = null
        var stripStart: Long? = null
        var stripSize: Long? = null
        var compression = 0L
        var orientation = 1
        val data = ByteBuffer.wrap(entries).order(order)
        for (i in 0 until count) {
            val offset = i * 12
            val tag = data.getShort(offset).toInt() and 0xffff
            val type = data.getShort(offset + 2).toInt() and 0xffff
            val total = unsignedInt(data.getInt(offset + 4))
            // Single inline 16-bit or 32-bit integer. Multi-strip arrays are
            // intentionally excluded until they can be decoded safely.
            if (total != 1L || type != 3 && type != 4) continue
            val value = if (type == 3) data.getShort(offset + 8).toLong() and 0xffff
                else unsignedInt(data.getInt(offset + 8))
            when (tag) {
                0x0103 -> compression = value
                0x0112 -> orientation = value.toInt()
                0x0111 -> stripStart = value
                0x0117 -> stripSize = value
                0x0201 -> jpegStart = value
                0x0202 -> jpegSize = value
            }
        }
        val start = jpegStart ?: if (compression == 7L) stripStart else null ?: return null
        val size = jpegSize ?: if (compression == 7L) stripSize else null ?: return null
        // Prevent gigantic allocations from malformed TIFF offsets.
        if (start < 8L || size < 4L || size > 32L * 1024 * 1024 ||
            start > length || size > length - start) return null
        return Slice(start, size.toInt(), orientation)
    }

    private fun unsignedInt(i: Int) = i.toLong() and 0xffff_ffffL

    private fun read(channel: FileChannel, offset: Long, length: Int): ByteArray? {
        if (offset < 0 || length < 0 || offset > channel.size() ||
            length.toLong() > channel.size() - offset) return null
        val bytes = ByteArray(length)
        val buffer = ByteBuffer.wrap(bytes)
        var index = offset
        while (buffer.hasRemaining()) {
            val count = channel.read(buffer, index)
            if (count <= 0) return null
            index += count
        }
        return bytes
    }
}

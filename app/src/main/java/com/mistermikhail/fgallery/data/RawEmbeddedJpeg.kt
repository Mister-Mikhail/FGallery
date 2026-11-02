package com.mistermikhail.fgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
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

    /** Extract camera JPEG without recompressing its pixels.
     * Writes only under app cache; never opens the RAW for writing.
     * Ensures orientation is embedded as JPEG EXIF metadata.
     */
    fun cached(context: Context, item: MediaItem): File? =
        File(File(context.cacheDir, "camera_raw_jpeg_v1"),
            "${StorageAccess.stableId(ThumbnailCache.key(item))}.jpg")
            .takeIf { it.isFile && it.length() > 0L }

    suspend fun extract(context: Context, item: MediaItem): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "camera_raw_jpeg_v1").apply { mkdirs() }
        val target = File(dir, "${StorageAccess.stableId(ThumbnailCache.key(item))}.jpg")
        if (target.isFile && target.length() > 0L) return@withContext target
        runCatching {
            val direct = StorageAccess.file(context, item)?.takeIf { it.isFile && it.canRead() }
            if (direct != null) {
                RandomAccessFile(direct, "r").use { extractFromChannel(it.channel, target) }
            } else {
                context.contentResolver.openFileDescriptor(item.uri, "r")?.use { fd ->
                    FileInputStream(fd.fileDescriptor).use { extractFromChannel(it.channel, target) }
                }
            }
        }.getOrNull()
    }

    private suspend fun extractFromChannel(channel: FileChannel, target: File): File? {
        val slice = locate(channel) ?: return null
        val temp = File.createTempFile("camera_", ".tmp", target.parentFile)
        try {
            FileOutputStream(temp).use { output ->
                var offset = slice.offset
                var remaining = slice.length.toLong()
                val buffer = ByteBuffer.allocate(128 * 1024)
                while (remaining > 0) {
                    currentCoroutineContext().ensureActive()
                    buffer.clear()
                    buffer.limit(minOf(buffer.capacity().toLong(), remaining).toInt())
                    val n = channel.read(buffer, offset)
                    if (n <= 0) return null
                    output.write(buffer.array(), 0, n)
                    remaining -= n
                    offset += n
                }
                output.fd.sync()
            }
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            if (slice.orientation in 2..8) {
                // ExifInterface writes metadata only; pixel bytes are not decoded
                // and recompressed, preserving camera JPEG quality.
                ExifInterface(temp.absolutePath).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, slice.orientation.toString())
                    saveAttributes()
                }
            }
            check(temp.renameTo(target))
            return target
        } finally { temp.delete() }
    }

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

    /**
     * Inspect the primary IFD, its next-IFD (often the DNG preview), and one
     * inline SubIFD. Camera DNG files frequently store their usable JPEG in
     * IFD1 rather than IFD0. Only small TIFF directories and JPEG signatures
     * are read: the original RAW data is never copied or decoded here.
     */
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
        if ((top.getShort(2).toInt() and 0xffff) != 42) return null

        data class Directory(val jpeg: Slice?, val next: Long, val subIfd: Long, val orientation: Int)
        fun directory(ifd: Long, inheritedOrientation: Int): Directory? {
            if (ifd < 8L || ifd > length - 2) return null
            val countBytes = read(channel, ifd, 2) ?: return null
            val count = ByteBuffer.wrap(countBytes).order(order).getShort().toInt() and 0xffff
            if (count > 4_096 || ifd + 2L + count * 12L + 4L > length) return null
            val entries = read(channel, ifd + 2L, count * 12) ?: return null
            val data = ByteBuffer.wrap(entries).order(order)
            var jpegStart: Long? = null
            var jpegSize: Long? = null
            var stripStart: Long? = null
            var stripSize: Long? = null
            var compression = 0L
            var orientation = inheritedOrientation
            var subIfd = 0L
            for (i in 0 until count) {
                val offset = i * 12
                val tag = data.getShort(offset).toInt() and 0xffff
                val type = data.getShort(offset + 2).toInt() and 0xffff
                val total = unsignedInt(data.getInt(offset + 4))
                // A TIFF array is an offset, not an inline scalar. Never
                // mistake a multi-strip array for the beginning of a JPEG.
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
                    0x014a -> if (type == 4) subIfd = value
                }
            }
            val nextOffset = ifd + 2L + count * 12L
            val nextBytes = read(channel, nextOffset, 4) ?: return null
            val next = unsignedInt(ByteBuffer.wrap(nextBytes).order(order).getInt())
            val start = jpegStart ?: if (compression == 7L) stripStart else null
            val size = jpegSize ?: if (compression == 7L) stripSize else null
            val candidate = if (start != null && size != null &&
                start >= 8L && size >= 4L && size <= 32L * 1024 * 1024 &&
                start <= length && size <= length - start) {
                val marker = read(channel, start, 2)
                if (marker != null && marker[0] == 0xff.toByte() &&
                    marker[1] == 0xd8.toByte()) Slice(start, size.toInt(), orientation)
                else null
            } else null
            return Directory(candidate, next, subIfd, orientation)
        }

        val firstOffset = unsignedInt(top.getInt(4))
        val first = directory(firstOffset, 1) ?: return null
        // Limit traversal and avoid cyclic/invalid directory pointers.
        val seen = hashSetOf(firstOffset)
        val candidates = mutableListOf<Slice>()
        first.jpeg?.let(candidates::add)
        for (offset in listOf(first.next, first.subIfd)) {
            if (offset >= 8L && seen.add(offset)) {
                directory(offset, first.orientation)?.jpeg?.let(candidates::add)
            }
        }
        // Prefer the largest camera JPEG. Some DNGs carry a tiny IFD0
        // thumbnail and a much better preview in IFD1.
        return candidates.maxByOrNull { it.length }
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

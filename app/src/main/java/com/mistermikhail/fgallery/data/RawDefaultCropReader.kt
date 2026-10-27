package com.mistermikhail.fgallery.data

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Lightweight TIFF/DNG DefaultCropSize probe, for RAW files whose TIFF wrapper
 * includes a JPEG preview with *different* dimensions from the sensor data.
 *
 * Never reads the raw pixel stream or mutates the original. If the required
 * tags are absent/malformed/outside the bounded prefix, LibRaw remains the fallback.
 */
internal object RawDefaultCropReader {
    private const val MAX_HEADER_BYTES = 256 * 1024
    private const val TAG_ORIENTATION = 274
    private const val TAG_SUB_IFDS = 330
    private const val TAG_IMAGE_WIDTH = 256
    private const val TAG_IMAGE_HEIGHT = 257
    private const val TAG_CROP_ORIGIN = 50719
    private const val TAG_CROP_SIZE = 50720

    fun read(context: Context, item: MediaItem): Pair<Int, Int>? {
        val ext = item.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext !in setOf("3fr", "fff", "dng")) return null
        // Some camera files are readable as direct files even when MediaStore
        // cannot provide a stream. Probe both access routes before expensive JNI.
        val local = runCatching {
            StorageAccess.file(context, item)?.takeIf { it.isFile && it.canRead() }
                ?.inputStream()?.use(::parseHeader)
        }.getOrNull()
        if (local != null) return local
        return runCatching {
            context.contentResolver.openInputStream(item.uri)?.use(::parseHeader)
        }.getOrNull()
    }

    private fun parseHeader(input: java.io.InputStream): Pair<Int, Int>? {
        val bytes = ByteArray(MAX_HEADER_BYTES)
        var read = 0
        while (read < bytes.size) {
            val n = input.read(bytes, read, bytes.size - read)
            if (n <= 0) break
            read += n
        }
        return parse(bytes.copyOf(read))
    }

    internal fun parse(data: ByteArray): Pair<Int, Int>? {
        if (data.size < 8) return null
        val order = when {
            data[0] == 0x49.toByte() && data[1] == 0x49.toByte() -> ByteOrder.LITTLE_ENDIAN
            data[0] == 0x4d.toByte() && data[1] == 0x4d.toByte() -> ByteOrder.BIG_ENDIAN
            else -> return null
        }
        val tiff = TiffHeader(data, order)
        if (tiff.u16(2) != 42) return null // Classic TIFF only, not BigTIFF.
        val root = tiff.directory(tiff.u32(4) ?: return null) ?: return null
        val orientation = root[TAG_ORIENTATION]?.let(tiff::firstValue) ?: 1
        val subIfds = root[TAG_SUB_IFDS]?.let { tag ->
            (0 until tag.count.coerceAtMost(8).toInt()).mapNotNull { tiff.element(tag, it)?.toLong() }
        }.orEmpty()
        for (offset in subIfds + listOfNotNull(tiff.u32(4))) {
            val dir = tiff.directory(offset) ?: continue
            val crop = dir[TAG_CROP_SIZE]?.let(tiff::pair) ?: continue
            val (width, height) = crop
            if (width <= 0 || height <= 0 || width > 100_000 || height > 100_000) continue
            val boundsWidth = dir[TAG_IMAGE_WIDTH]?.let(tiff::firstValue)
            val boundsHeight = dir[TAG_IMAGE_HEIGHT]?.let(tiff::firstValue)
            val origin = dir[TAG_CROP_ORIGIN]?.let(tiff::pair) ?: (0 to 0)
            if (boundsWidth == null || boundsHeight == null ||
                origin.first < 0 || origin.second < 0 ||
                origin.first.toLong() + width > boundsWidth ||
                origin.second.toLong() + height > boundsHeight) continue
            return if (orientation in 5..8) height to width else width to height
        }
        return null
    }

    private data class Tag(val type: Int, val count: Long, val valuePosition: Int)

    private class TiffHeader(bytes: ByteArray, order: ByteOrder) {
        private val buffer = ByteBuffer.wrap(bytes).order(order)

        fun u16(pos: Int): Int? =
            if (pos >= 0 && pos <= buffer.limit() - 2) buffer.getShort(pos).toInt() and 0xffff else null

        fun u32(pos: Int): Long? =
            if (pos >= 0 && pos <= buffer.limit() - 4) buffer.getInt(pos).toLong() and 0xffff_ffffL else null

        fun directory(offset: Long): Map<Int, Tag>? {
            if (offset > Int.MAX_VALUE || offset < 0) return null
            val pos = offset.toInt()
            val count = u16(pos) ?: return null
            if (count > 512 || pos.toLong() + 2 + count.toLong() * 12 + 4 > buffer.limit()) return null
            return buildMap {
                repeat(count) { index ->
                    val entry = pos + 2 + index * 12
                    val tag = u16(entry) ?: return@repeat
                    val type = u16(entry + 2) ?: return@repeat
                    val elements = u32(entry + 4) ?: return@repeat
                    put(tag, Tag(type, elements, entry + 8))
                }
            }
        }

        private fun dataPosition(tag: Tag): Int? {
            val unit = when (tag.type) {
                3 -> 2L // SHORT
                4 -> 4L // LONG
                else -> return null
            }
            if (tag.count <= 0 || tag.count > 4096 || tag.count * unit > Int.MAX_VALUE) return null
            return if (tag.count * unit <= 4L) tag.valuePosition
            else u32(tag.valuePosition)?.takeIf { it <= Int.MAX_VALUE }?.toInt()
        }

        fun element(tag: Tag, index: Int): Int? {
            if (index < 0 || index >= tag.count) return null
            val start = dataPosition(tag) ?: return null
            return when (tag.type) {
                3 -> u16(start + index * 2)
                4 -> u32(start + index * 4)?.takeIf { it <= Int.MAX_VALUE }?.toInt()
                else -> null
            }
        }

        fun firstValue(tag: Tag): Int? = element(tag, 0)
        fun pair(tag: Tag): Pair<Int, Int>? {
            if (tag.count != 2L) return null
            val a = element(tag, 0) ?: return null
            val b = element(tag, 1) ?: return null
            return a to b
        }
    }
}

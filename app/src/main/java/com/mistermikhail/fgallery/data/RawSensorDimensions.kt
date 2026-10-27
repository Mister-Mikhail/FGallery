package com.mistermikhail.fgallery.data

import android.content.Context
import com.homesoft.photo.libraw.LibRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Returns sensor-area dimensions, not the TIFF/EXIF embedded JPEG preview size.
 * The TIFF DefaultCropSize fast path also avoids an unnecessary 100MP RAW unpack.
 */
internal object RawSensorDimensions {
    // Invalidate previous cached values which used overscanned LibRaw X2D sizes.
    private const val PREFERENCES = "raw_sensor_dimensions_v3"

    // AndroidLibRaw.open() and openFd() call unpack() in JNI. On large files
    // this may allocate >200MB. A single metadata probe runs at a time and
    // cache lookup happens inside the lock, avoiding redundant simultaneous unpacks.
    private val metadataProbeMutex = Mutex()

    internal fun orientedSize(width: Int, height: Int, libRawFlip: Int): Pair<Int, Int>? {
        if (width <= 0 || height <= 0) return null
        return if (libRawFlip == LibRaw.ROTATE_90 || libRawFlip == LibRaw.ROTATE_270)
            height to width else width to height
    }

    suspend fun read(context: Context, item: MediaItem): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        if (item.kind != MediaKind.RAW) return@withContext null
        metadataProbeMutex.withLock {
            val key = StorageAccess.stableId(
                item.uriKey + "|" + item.dateModifiedMillis + "|" + item.sizeBytes
            ).toString()
            val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

            prefs.getString(key, null)?.split('x')?.takeIf { it.size == 2 }?.let { dimensions ->
                val width = dimensions[0].toIntOrNull()
                val height = dimensions[1].toIntOrNull()
                if (width != null && height != null && width > 0 && height > 0)
                    return@withLock width to height
            }

            // Trust a validated sensor SubIFD crop, not an embedded IFD0 JPEG.
            val crop = RawDefaultCropReader.read(context, item)
            if (crop != null) {
                prefs.edit().putString(key, "${crop.first}x${crop.second}").apply()
                return@withLock crop
            }

            val result = runCatching {
                LibRaw().use { raw ->
                    val file = StorageAccess.file(context, item)?.takeIf { it.isFile && it.canRead() }
                    val status = if (file != null) raw.open(file.canonicalPath)
                    else context.contentResolver.openFileDescriptor(item.uri, "r")?.use { descriptor ->
                        raw.openFd(descriptor.fd)
                    } ?: error("Невозможно открыть RAW-файл")
                    check(status == 0) { "LibRaw не прочитал заголовок RAW (код $status)" }
                    orientedSize(raw.width, raw.height, raw.orientation)
                        ?: error("Размеры RAW не определены")
                }
            }.getOrNull()
            result?.let { (w, h) -> prefs.edit().putString(key, "${w}x${h}").apply() }
            result
        }
    }
}

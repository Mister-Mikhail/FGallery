package com.mistermikhail.fgallery.data

import android.content.Context
import com.homesoft.photo.libraw.LibRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Metadata dimensions from LibRaw rather than embedded JPEG EXIF/MediaStore preview. */
internal object RawSensorDimensions {
    private const val PREFERENCES = "raw_sensor_dimensions_v1"

    // AndroidLibRaw.open()/openFd() also unpack the Bayer buffer in JNI. On 100 MP
    // files this may temporarily allocate hundreds of megabytes. Serialize metadata
    // probes and repeat the cache lookup under the lock to avoid duplicate unpacks.
    private val metadataProbeMutex = Mutex()

    internal fun orientedSize(width: Int, height: Int, libRawFlip: Int): Pair<Int, Int>? {
        if (width <= 0 || height <= 0) return null
        // LibRaw flip codes 5,6 mean 270°/90° rotation; 3 means 180°.
        return if (libRawFlip == LibRaw.ROTATE_90 || libRawFlip == LibRaw.ROTATE_270)
            height to width else width to height
    }

    suspend fun read(context: Context, item: MediaItem): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        if (item.kind != MediaKind.RAW) return@withContext null
        metadataProbeMutex.withLock {
        val key = StorageAccess.stableId(item.uriKey + "|" + item.dateModifiedMillis + "|" + item.sizeBytes).toString()
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        prefs.getString(key, null)?.split('x')?.takeIf { it.size == 2 }?.let { dimensions ->
            val width = dimensions[0].toIntOrNull()
            val height = dimensions[1].toIntOrNull()
            if (width != null && height != null && width > 0 && height > 0)
                return@withLock width to height
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

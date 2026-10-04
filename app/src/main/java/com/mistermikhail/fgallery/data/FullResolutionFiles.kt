package com.mistermikhail.fgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.homesoft.photo.libraw.LibRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** A disk-backed full-size source lets the viewer decode only visible PNG tiles. */
object FullResolutionFiles {
    private val lock = Mutex()

    suspend fun prepare(context: Context, item: MediaItem): File = withContext(Dispatchers.IO) {
        lock.withLock {
            val directory = File(context.cacheDir, "full_resolution_v1").apply { mkdirs() }
            val target = File(directory, "${item.id}_${item.dateModifiedMillis}_${item.sizeBytes}.png")
            if (target.exists() && target.length() > 0) return@withLock target
            val input = if (item.uri.scheme == "file") File(item.uri.path!!) else
                File.createTempFile("source_", ".bin", directory).also { file ->
                    context.contentResolver.openInputStream(item.uri)?.use { source -> file.outputStream().use { source.copyTo(it) } }
                        ?: error("Нет доступа к исходному файлу")
                }
            val output = File.createTempFile("decode_", ".png", directory)
            try {
                currentCoroutineContext().ensureActive()
                if (item.kind == MediaKind.RAW) {
                    LibRaw().use { raw ->
                        raw.setHalfSize(false)
                        raw.setCameraWhiteBalance(true)
                        raw.setOutputColorSpace(LibRaw.COLORSPACE_SRGB)
                        val bitmap = raw.decodeBitmap(input.absolutePath,
                            BitmapFactory.Options().apply { inSampleSize = 1; inPreferredConfig = Bitmap.Config.ARGB_8888 })
                            ?: error("RAW не удалось декодировать")
                        try { output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
                        finally { bitmap.recycle() }
                    }
                } else {
                    val metadata = TiffNative.metadata(input.absolutePath) ?: error("Некорректный TIFF")
                    check(TiffNative.writePng(input.absolutePath, output.absolutePath)) { "Не удалось прочитать TIFF полного разрешения" }
                    if (metadata[2] in 2..8) ExifInterface(output).apply {
                        setAttribute(ExifInterface.TAG_ORIENTATION, metadata[2].toString())
                        saveAttributes()
                    }
                    val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(output.absolutePath, dimensions)
                    check(dimensions.outWidth == metadata[0] && dimensions.outHeight == metadata[1]) { "TIFF декодирован не полностью" }
                }
                currentCoroutineContext().ensureActive()
                check(output.length() > 0 && output.renameTo(target)) { "Не удалось подготовить полный размер" }
                // Keep the opened source plus a small set of recently viewed full-size files.
                directory.listFiles()?.filter { it != target && it.name.endsWith(".png") && !it.name.startsWith("decode_") }
                    ?.sortedByDescending(File::lastModified)?.drop(2)?.forEach(File::delete)
                target
            } finally {
                if (item.uri.scheme != "file") input.delete()
                output.delete()
            }
        }
    }
}

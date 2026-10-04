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
            val input = if (item.uri.scheme == "file") File(item.uri.path!!) else File.createTempFile("source_", ".bin", directory)
            val output = File.createTempFile("decode_", ".png", directory)
            try {
                if (item.uri.scheme != "file") context.contentResolver.openInputStream(item.uri)?.use { source ->
                    input.outputStream().use { destination ->
                        val buffer = ByteArray(65536)
                        while (true) { currentCoroutineContext().ensureActive(); val n = source.read(buffer); if (n < 0) break; destination.write(buffer, 0, n) }
                    }
                } ?: error("Нет доступа к исходному файлу")
                currentCoroutineContext().ensureActive()
                if (item.kind == MediaKind.RAW) {
                    LibRaw().use { raw ->
                        raw.setHalfSize(false)
                        raw.setCameraWhiteBalance(true)
                        raw.setOutputColorSpace(LibRaw.COLORSPACE_SRGB)
                        check(raw.open(input.absolutePath) == 0) { "RAW не удалось открыть" }
                        raw.setQuality(3)
                        check(raw.dcrawProcess() == 0) { "RAW не удалось декодировать" }
                        val bitmap = raw.getMutableBitmap(Bitmap.Config.ARGB_8888)
                            ?: error("RAW не удалось преобразовать")
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
            } catch (e: OutOfMemoryError) {
                throw IllegalStateException("Недостаточно памяти для обработки исходного файла", e)
            } finally {
                if (item.uri.scheme != "file") input.delete()
                output.delete()
            }
        }
    }
}

package com.mistermikhail.fgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.homesoft.photo.libraw.LibRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** A disk-backed full-size source lets the viewer decode only visible PNG tiles. */
object FullResolutionFiles {
    private val lock = Mutex()

    fun cached(context: Context, item: MediaItem): File? = File(File(context.cacheDir, "full_resolution_v2"),
        "${StorageAccess.stableId(ThumbnailCache.key(item))}.png").takeIf { it.exists() && it.length() > 0 }

    suspend fun prepare(context: Context, item: MediaItem): File = withContext(Dispatchers.IO) {
        cached(context, item)?.let { it.setLastModified(System.currentTimeMillis()); return@withContext it }
        lock.withLock {
            val directory = File(context.cacheDir, "full_resolution_v2").apply { mkdirs() }
            val target = File(directory, "${StorageAccess.stableId(ThumbnailCache.key(item))}.png")
            if (target.exists() && target.length() > 0) { target.setLastModified(System.currentTimeMillis()); return@withLock target }
            val direct = StorageAccess.file(context, item)?.takeIf { it.isFile && it.canRead() }
            val input = direct ?: File.createTempFile("source_", ".bin", directory)
            val output = File.createTempFile("decode_", ".png", directory)
            try {
                if (direct == null) context.contentResolver.openInputStream(item.uri)?.use { source ->
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
                        coroutineScope {
                            val cancelDecode = launch(Dispatchers.Default) {
                                try { awaitCancellation() } finally { raw.setCancelFlag() }
                            }
                            try {
                                val result = raw.dcrawProcess()
                                currentCoroutineContext().ensureActive()
                                check(result == 0) { "RAW не удалось декодировать" }
                                val bitmap = raw.getMutableBitmap(Bitmap.Config.ARGB_8888)
                                    ?: error("RAW не удалось преобразовать")
                                try { check(TiffNative.writeBitmapPng(bitmap, output.absolutePath)) { "Не удалось сохранить полный размер RAW" } }
                                finally { bitmap.recycle() }
                            } finally { withContext(NonCancellable) { cancelDecode.cancelAndJoin() } }
                        }
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
                    ?.sortedByDescending(File::lastModified)?.let { files ->
                        var bytes = target.length()
                        files.forEach { file -> bytes += file.length(); if (bytes > 1024L * 1024 * 1024) file.delete() }
                    }
                target
            } catch (e: OutOfMemoryError) {
                throw IllegalStateException("Недостаточно памяти для обработки исходного файла", e)
            } finally {
                if (direct == null) input.delete()
                output.delete()
            }
        }
    }
}

package com.mistermikhail.fgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.beyka.tiffbitmapfactory.TiffBitmapFactory
import java.io.File

object TiffImages {
    fun isTiff(name: String, mime: String? = null): Boolean =
        name.endsWith(".tif", true) || name.endsWith(".tiff", true) || mime?.lowercase() in setOf("image/tiff", "image/x-tiff")

    /** Libtiff supports strips/tiles, LZW, Deflate, JPEG and 8/16-bit source samples. */
    @Synchronized fun decode(file: File, edge: Int = 4096): Bitmap {
        val bounds = TiffBitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inThrowException = true
            inUseOrientationTag = true
        }
        TiffBitmapFactory.decodeFile(file, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Некорректный TIFF" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > edge ||
            bounds.outWidth.toLong() * bounds.outHeight / sample / sample > 12_000_000L) sample *= 2
        while (sample <= 256) {
            val options = TiffBitmapFactory.Options().apply {
                inSampleSize = sample
                inAvailableMemory = 128L * 1024 * 1024
                inThrowException = false
                inUseOrientationTag = true
            }
            val bitmap = TiffBitmapFactory.decodeFile(file, options)
            if (bitmap != null) return bitmap
            sample *= 2
        }
        error("Не удалось декодировать TIFF")
    }

    fun decode(context: Context, uri: Uri, edge: Int): Bitmap {
        if (uri.scheme == "file") return decode(File(uri.path!!), edge)
        val file = File.createTempFile("tiff_source_", ".tif", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: error("Нет доступа к TIFF")
            return decode(file, edge)
        } finally { file.delete() }
    }

    fun cropSource(context: Context, uri: Uri): Uri {
        val bitmap = decode(context, uri, 8192)
        val file = File.createTempFile("tiff_crop_", ".png", context.cacheDir)
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}

class TiffDecoder(private val result: SourceFetchResult) : Decoder {
    override suspend fun decode(): DecodeResult = withContext(Dispatchers.IO) {
        val bitmap = TiffImages.decode(result.source.file().toFile())
        DecodeResult(bitmap.asImage(), isSampled = true)
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val peek = result.source.source().peek()
            if (!peek.request(4)) return null
            val bytes = peek.readByteArray(4)
            val tiff = bytes.contentEquals(byteArrayOf(73, 73, 42, 0)) || bytes.contentEquals(byteArrayOf(77, 77, 0, 42))
            return if (tiff) TiffDecoder(result) else null
        }
    }
}

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
import android.graphics.Matrix
import java.io.File

object TiffImages {
    fun isTiff(name: String, mime: String? = null): Boolean =
        name.endsWith(".tif", true) || name.endsWith(".tiff", true) || mime?.lowercase() in setOf("image/tiff", "image/x-tiff")

    /** Libtiff supports strips/tiles, LZW, Deflate, JPEG and 8/16-bit source samples. */
    @Synchronized fun decode(file: File, edge: Int = 4096): Bitmap {
        val decoded = TiffNative.decode(file.absolutePath, edge) ?: error("Не удалось декодировать TIFF")
        val width = decoded[0]; val height = decoded[1]; val orientation = decoded[2]
        val bitmap = Bitmap.createBitmap(decoded, 3, width, width, height, Bitmap.Config.ARGB_8888)
        if (orientation !in 2..8) return bitmap
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> { setRotate(180f); postScale(-1f, 1f) }
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
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

internal object TiffNative {
    init {
        System.loadLibrary("tiff")
        System.loadLibrary("tiffconverter")
        System.loadLibrary("fgallery_tiff")
    }
    external fun decode(path: String, edge: Int): IntArray?
    external fun metadata(path: String): IntArray?
    external fun writePng(source: String, destination: String): Boolean
}

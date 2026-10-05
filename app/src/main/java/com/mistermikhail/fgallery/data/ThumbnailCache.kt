package com.mistermikhail.fgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ThumbnailCache {
    private const val DIRECTORY = "fgallery_thumbnails_v3"
    private const val FAST_EDGE_PX = 720
    private const val HIGH_EDGE_PX = 1920
    private val generationLocks = ConcurrentHashMap<String, Mutex>()
    private val decoders = Semaphore(2)

    fun key(item: MediaItem): String {
        val source = item.sourcePath.ifBlank { if (item.uri.scheme == "file") item.uri.path.orEmpty() else item.uriKey }
        return "${item.kind}/${StorageAccess.stableId(source)}/${item.dateModifiedMillis}/${item.sizeBytes}"
    }

    fun fileFor(context: Context, item: MediaItem, highQuality: Boolean = false): File {
        val version = item.dateModifiedMillis.takeIf { it > 0L } ?: item.dateTakenMillis
        val tier = if (highQuality) "hq" else "fast"
        return File(File(context.cacheDir, DIRECTORY), "${StorageAccess.stableId(key(item))}_${version}_${tier}.png")
    }

    suspend fun preload(context: Context, items: List<MediaItem>, highQuality: Boolean = false): Int =
        withContext(Dispatchers.IO) {
            var generated = 0
            items.forEach { if (ensure(context, it, highQuality) != null) generated++ }
            generated
        }

    suspend fun ensure(context: Context, item: MediaItem, highQuality: Boolean = false): File? =
        withContext(Dispatchers.IO) {
            val target = fileFor(context, item, highQuality)
            if (target.exists() && target.length() > 0L) return@withContext target
            generationLocks.getOrPut(target.path) { Mutex() }.withLock {
                decoders.withPermit {
                val target = fileFor(context, item, highQuality)
                if (target.exists() && target.length() > 0L) return@withPermit target
                target.parentFile?.mkdirs()
                val edge = if (highQuality) HIGH_EDGE_PX else FAST_EDGE_PX
                val bitmap = runCatching { decode(context, item, edge) }.getOrNull()
                    ?: return@withPermit null
                val temp = File.createTempFile("preview_", ".tmp", target.parentFile)
                try {
                    check(TiffNative.writeBitmapPng(bitmap, temp.absolutePath))
                    check(temp.renameTo(target))
                    target
                } catch (_: Exception) {
                    null
                } finally {
                    temp.delete()
                    bitmap.recycle()
                }
                }
            }
        }

    fun cached(context: Context, item: MediaItem): File? =
        fileFor(context, item, true).takeIf { it.exists() && it.length() > 0 }
            ?: fileFor(context, item).takeIf { it.exists() && it.length() > 0 }

    private fun decode(context: Context, item: MediaItem, edge: Int): Bitmap? {
        FullResolutionFiles.cached(context, item)?.let { file ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > edge) options.inSampleSize *= 2
            options.inJustDecodeBounds = false
            return BitmapFactory.decodeFile(file.path, options)
        }
        return when (item.kind) {
        MediaKind.VIDEO -> representativeVideoFrame(context, item, edge)
        MediaKind.PDF -> PdfSession(context, item.uri).use { it.render(0, edge) }
        MediaKind.SVG -> VisualDocuments.svgPreview(context, item.uri, edge)
        MediaKind.IMAGE, MediaKind.RAW ->
            if (TiffImages.isTiff(item.name, item.mimeType)) TiffImages.decode(context, item.uri, edge)
            else runCatching { context.contentResolver.loadThumbnail(item.uri, Size(edge, edge), null) }
                .getOrNull() ?: embeddedPreview(context, item) ?: if (item.kind == MediaKind.IMAGE) sampled(context, item, edge) else null
    }

    }

    private fun embeddedPreview(context: Context, item: MediaItem): Bitmap? = runCatching {
        context.contentResolver.openInputStream(item.uri)?.use { input ->
            val exif = ExifInterface(input)
            exif.thumbnailBitmap?.let { bitmap ->
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                val matrix = android.graphics.Matrix().apply {
                    when (orientation) { 3 -> setRotate(180f); 6 -> setRotate(90f); 8 -> setRotate(-90f) }
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
            }
        }
    }.getOrNull()

    private fun sampled(context: Context, item: MediaItem, edge: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > edge) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        return context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    fun rawPreview(context: Context, item: MediaItem): Bitmap? {
        val direct = StorageAccess.file(context, item)?.takeIf { it.isFile && it.canRead() }
        val input = direct ?: File.createTempFile("raw_preview_", ".bin", context.cacheDir)
        try {
            if (direct == null) context.contentResolver.openInputStream(item.uri)?.use { source -> input.outputStream().use { source.copyTo(it) } } ?: return null
            return com.homesoft.photo.libraw.LibRaw().use { raw ->
                raw.setHalfSize(true); raw.setQuality(0)
                raw.setCameraWhiteBalance(true)
                raw.setOutputColorSpace(com.homesoft.photo.libraw.LibRaw.COLORSPACE_SRGB)
                if (raw.open(input.path) != 0 || raw.dcrawProcess() != 0) return@use null
                raw.getMutableBitmap(Bitmap.Config.ARGB_8888)?.let { bitmap ->
                    val scale = minOf(1f, FAST_EDGE_PX.toFloat() / maxOf(bitmap.width, bitmap.height))
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                        .also { if (it !== bitmap) bitmap.recycle() }
                }
            }
        } finally { if (direct == null) input.delete() }
    }

    /** Try multiple positions: a sync frame at time zero is often just a black leader. */
    fun representativeVideoFrame(context: Context, item: MediaItem, edge: Int = 720): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, item.uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: item.durationMillis
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()?.coerceAtLeast(1) ?: edge
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()?.coerceAtLeast(1) ?: edge
            val scale = minOf(1f, edge.toFloat() / maxOf(width, height))
            val times = listOf(duration / 2, duration / 4, duration * 3 / 4, 1_000L, 0L).distinct()
            for (time in times) {
                val frame = runCatching {
                    retriever.getScaledFrameAtTime(
                        time.coerceIn(0L, (duration - 1).coerceAtLeast(0L)) * 1_000,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1),
                    )
                }.getOrNull() ?: continue
                if (!isBlack(frame)) return frame
                frame.recycle()
            }
            return null
        } finally {
            retriever.release()
        }
    }

    private fun isBlack(bitmap: Bitmap): Boolean {
        for (y in 1..7) for (x in 1..7) {
            val pixel = bitmap.getPixel((bitmap.width - 1) * x / 8, (bitmap.height - 1) * y / 8)
            if (((pixel shr 16) and 255) > 20 || ((pixel shr 8) and 255) > 20 || (pixel and 255) > 20) return false
        }
        return true
    }
}

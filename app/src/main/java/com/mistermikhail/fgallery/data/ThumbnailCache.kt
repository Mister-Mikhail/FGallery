package com.mistermikhail.fgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ThumbnailCache {
    private const val DIRECTORY = "fgallery_thumbnails_v2"
    private const val FAST_EDGE_PX = 720
    private const val HIGH_EDGE_PX = 1920
    private val generationLock = Mutex()

    fun fileFor(context: Context, item: MediaItem, highQuality: Boolean = false): File {
        val version = item.dateModifiedMillis.takeIf { it > 0L } ?: item.dateTakenMillis
        val tier = if (highQuality) "hq" else "fast"
        return File(File(context.cacheDir, DIRECTORY), "${item.kind.name.lowercase()}_${item.id}_${version}_${tier}.png")
    }

    suspend fun preload(context: Context, items: List<MediaItem>, highQuality: Boolean = false): Int =
        withContext(Dispatchers.IO) {
            var generated = 0
            items.forEach { if (ensure(context, it, highQuality) != null) generated++ }
            generated
        }

    suspend fun ensure(context: Context, item: MediaItem, highQuality: Boolean = false): File? =
        withContext(Dispatchers.IO) {
            generationLock.withLock {
                val target = fileFor(context, item, highQuality)
                if (target.exists() && target.length() > 0L) return@withLock target
                target.parentFile?.mkdirs()
                val edge = if (highQuality) HIGH_EDGE_PX else FAST_EDGE_PX
                val bitmap = runCatching { decode(context, item, edge) }.getOrNull()
                    ?: return@withLock null
                val temp = File.createTempFile("preview_", ".tmp", target.parentFile)
                try {
                    check(FileOutputStream(temp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) })
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

    private fun decode(context: Context, item: MediaItem, edge: Int): Bitmap? = when (item.kind) {
        MediaKind.VIDEO -> representativeVideoFrame(context, item, edge)
        MediaKind.PDF -> PdfSession(context, item.uri).use { it.render(0, edge) }
        MediaKind.SVG -> VisualDocuments.svgPreview(context, item.uri, edge)
        MediaKind.IMAGE, MediaKind.RAW ->
            runCatching { context.contentResolver.loadThumbnail(item.uri, Size(edge, edge), null) }
                .getOrNull() ?: context.contentResolver.openInputStream(item.uri)?.use { input ->
                    val bytes = runCatching { ExifInterface(input).thumbnailBytes }.getOrNull()
                    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
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

package com.mistermikhail.fgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ThumbnailCache {
    private const val DIRECTORY = "fgallery_thumbnails"
    private const val FAST_EDGE_PX = 720
    private const val HIGH_EDGE_PX = 1920

    fun fileFor(
        context: Context,
        item: MediaItem,
        highQuality: Boolean = false,
    ): File {
        val version = item.dateModifiedMillis
            .takeIf { it > 0L }
            ?: item.dateTakenMillis

        val tier = if (highQuality) "hq" else "fast"
        val name =
            "${item.kind.name.lowercase()}_${item.id}_${version}_${tier}.jpg"

        return File(File(context.cacheDir, DIRECTORY), name)
    }

    suspend fun preload(
        context: Context,
        items: List<MediaItem>,
        highQuality: Boolean = false,
    ): Int = withContext(Dispatchers.IO) {
        var generated = 0
        items.forEach { item ->
            if (preloadOne(context, item, highQuality)) {
                generated += 1
            }
        }
        generated
    }

    private fun preloadOne(
        context: Context,
        item: MediaItem,
        highQuality: Boolean,
    ): Boolean {
        val directory = File(context.cacheDir, DIRECTORY)
        if (!directory.exists()) directory.mkdirs()

        val target = fileFor(
            context = context,
            item = item,
            highQuality = highQuality,
        )

        if (target.exists() && target.length() > 0L) return false

        val version = item.dateModifiedMillis
            .takeIf { it > 0L }
            ?: item.dateTakenMillis

        val rowPrefix = "${item.kind.name.lowercase()}_${item.id}_"
        val currentVersionPrefix =
            "${item.kind.name.lowercase()}_${item.id}_${version}_"

        directory.listFiles()
            ?.filter {
                it.name.startsWith(rowPrefix) &&
                    !it.name.startsWith(currentVersionPrefix)
            }
            ?.forEach(File::delete)

        val edge = if (highQuality) HIGH_EDGE_PX else FAST_EDGE_PX
        val jpegQuality = if (highQuality) 96 else 90

        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(
                    item.uri,
                    Size(edge, edge),
                    null,
                )
            } else {
                when (item.kind) {
                    MediaKind.VIDEO -> MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        item.id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null,
                    )

                    MediaKind.IMAGE,
                    MediaKind.RAW,
                    -> MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver,
                        item.id,
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null,
                    )
                }
            }
        }.getOrNull() ?: return false

        val written = runCatching {
            FileOutputStream(target).use { stream ->
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    jpegQuality,
                    stream,
                )
            }
        }.getOrDefault(false)

        bitmap.recycle()

        if (!written) {
            target.delete()
            return false
        }

        return true
    }
}

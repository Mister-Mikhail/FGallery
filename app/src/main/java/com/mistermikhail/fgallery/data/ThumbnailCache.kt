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
    private const val EDGE_PX = 420

    fun fileFor(
        context: Context,
        item: MediaItem,
    ): File {
        val version = item.dateModifiedMillis
            .takeIf { it > 0L }
            ?: item.dateTakenMillis
        val name = "${item.kind.name.lowercase()}_${item.id}_${version}.jpg"
        return File(File(context.cacheDir, DIRECTORY), name)
    }

    suspend fun preload(
        context: Context,
        items: List<MediaItem>,
    ): Int = withContext(Dispatchers.IO) {
        var generated = 0
        items.forEach { item ->
            if (preloadOne(context, item)) generated += 1
        }
        generated
    }

    private fun preloadOne(
        context: Context,
        item: MediaItem,
    ): Boolean {
        val directory = File(context.cacheDir, DIRECTORY)
        if (!directory.exists()) directory.mkdirs()

        val target = fileFor(context, item)
        if (target.exists() && target.length() > 0L) return false

        val prefix = "${item.kind.name.lowercase()}_${item.id}_"
        directory.listFiles()
            ?.filter { it.name.startsWith(prefix) && it != target }
            ?.forEach(File::delete)

        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(
                    item.uri,
                    Size(EDGE_PX, EDGE_PX),
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
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
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

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
    ) = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, DIRECTORY)
        if (!directory.exists()) directory.mkdirs()

        items.forEach { item ->
            val target = fileFor(context, item)
            if (target.exists() && target.length() > 0L) return@forEach

            // Remove older cached versions for this MediaStore row.
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
            }.getOrNull() ?: return@forEach

            runCatching {
                FileOutputStream(target).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
                }
            }.onFailure {
                target.delete()
            }

            bitmap.recycle()
        }
    }
}

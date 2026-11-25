package com.mistermikhail.fgallery.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.FileObserver
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Match observer echoes to sources the app actually moved. */
internal class RecycleBinNotifications(private val context: Context) {
    private data class MediaKey(val volume: String, val id: Long)
    private data class Move(val from: String?, val to: String?)
    private val movedPaths = ConcurrentHashMap.newKeySet<String>()
    private val movedMedia = ConcurrentHashMap<MediaKey, Move>()

    fun record(source: MediaItem, destination: MediaItem) {
        val from = localPath(source)
        val to = localPath(destination)
        from?.let(movedPaths::add)
        mediaKey(source.uri)?.let { movedMedia[it] = Move(from, to) }
    }

    fun isOwnFileChange(folder: String, event: Int, name: String?): Boolean {
        if (name == StorageAccess.BIN) return true
        if (name == null) return false
        val path = File(folder, name).path
        if (event and (FileObserver.DELETE or FileObserver.MOVED_FROM) != 0) return path in movedPaths
        movedPaths.remove(path)
        return false
    }

    suspend fun isOwnMediaChange(uri: Uri, flags: Int): Boolean {
        val key = mediaKey(uri) ?: return false
        val alternate = when (key.volume) {
            MediaStore.VOLUME_EXTERNAL -> key.copy(volume = MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaStore.VOLUME_EXTERNAL_PRIMARY -> key.copy(volume = MediaStore.VOLUME_EXTERNAL)
            else -> null
        }
        if (flags and ContentResolver.NOTIFY_INSERT != 0) {
            movedMedia.remove(key)
            alternate?.let(movedMedia::remove)
            return false
        }
        val move = movedMedia[key] ?: alternate?.let(movedMedia::get) ?: return false
        if (flags and ContentResolver.NOTIFY_DELETE != 0) return true
        // Some providers send update or untyped notifications for removals.
        // Verify only the notified row; it may have been replaced externally.
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use true
                    val path = cursor.getString(0)
                    path != null && (path == move.to || (path == move.from && !File(path).exists()))
                } == true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                false
            }
        }
    }

    fun clear() {
        movedPaths.clear()
        movedMedia.clear()
    }

    private fun localPath(item: MediaItem): String? =
        item.sourcePath.takeIf { it.startsWith("/") } ?: item.uri.path?.takeIf { item.uri.scheme == "file" }

    private fun mediaKey(uri: Uri): MediaKey? {
        if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY) return null
        val parts = uri.pathSegments
        if (parts.size < 3 || parts[1] !in setOf("file", "images", "video", "downloads")) return null
        return MediaKey(parts.first(), parts.last().toLongOrNull() ?: return null)
    }
}

package bar.fgallery.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

class MediaRepository(private val context: Context) {
    fun loadMedia(): List<MediaItem> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.MIME_TYPE, MediaStore.Files.FileColumns.BUCKET_ID, MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME, MediaStore.Files.FileColumns.DATE_MODIFIED, MediaStore.Files.FileColumns.WIDTH, MediaStore.Files.FileColumns.HEIGHT)
        val selection = buildString {
            append("("); append(MediaStore.Files.FileColumns.MEDIA_TYPE); append("=? OR "); append(MediaStore.Files.FileColumns.MEDIA_TYPE); append("=?)")
            if (Build.VERSION.SDK_INT >= 30) { append(" AND "); append(MediaStore.MediaColumns.IS_TRASHED); append("=0") }
        }
        val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        val result = mutableListOf<MediaItem>()
        context.contentResolver.query(collection, projection, selection, args, MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC")?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID); val name = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME); val mime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE); val bucketId = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID); val bucketName = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME); val date = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED); val width = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH); val height = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            while (cursor.moveToNext()) { val mediaId = cursor.getLong(id); result += MediaItem(mediaId, ContentUris.withAppendedId(collection, mediaId), cursor.getString(name) ?: "", cursor.getString(bucketId) ?: "unknown", cursor.getString(bucketName) ?: "Без имени", cursor.getString(mime) ?: "application/octet-stream", cursor.getLong(date) * 1000, cursor.getInt(width), cursor.getInt(height)) }
        }
        return result
    }
    fun folders(items: List<MediaItem>): List<MediaFolder> = items.groupBy { it.bucketId }.map { (id, media) -> MediaFolder(id, media.first().bucketName, media.size, media.firstOrNull()) }.sortedBy { it.name.lowercase() }
}

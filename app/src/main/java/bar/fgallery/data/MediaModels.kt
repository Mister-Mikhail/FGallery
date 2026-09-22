package bar.fgallery.data

import android.net.Uri

data class MediaItem(val id: Long, val uri: Uri, val name: String, val bucketId: String, val bucketName: String, val mimeType: String, val dateTaken: Long, val width: Int, val height: Int)
data class MediaFolder(val id: String, val name: String, val count: Int, val cover: MediaItem?)

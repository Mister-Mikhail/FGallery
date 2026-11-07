package com.mistermikhail.fgallery.data

import android.content.Context
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

object MediaLocation {
    fun display(context: Context, item: MediaItem): String {
        if (item.sourcePath.isNotBlank()) return item.sourcePath
        if (item.uri.scheme == "file") return item.uri.path.orEmpty()
        if (item.uri.authority == "com.android.externalstorage.documents") {
            val id = runCatching { DocumentsContract.getDocumentId(item.uri) }.getOrNull()
            if (id != null && ':' in id) {
                val volume = id.substringBefore(':')
                val root = if (volume == "primary") Environment.getExternalStorageDirectory().path else "/storage/$volume"
                return "$root/${id.substringAfter(':')}"
            }
        }
        if (item.relativePath.isNotBlank()) {
            val root = StorageAccess.mounted(context).firstOrNull { it.id == item.storageId }?.directory
            if (root != null) return File(root, item.relativePath.trim('/') + "/" + item.name).path
            return "${item.storageName}/${item.relativePath.trim('/')}/${item.name}"
        }
        return item.uriKey
    }
}

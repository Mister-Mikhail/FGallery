package com.mistermikhail.fgallery.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/** Renames never overwrite an existing destination, on any storage provider. */
object MediaRename {
    fun rename(context: Context, item: MediaItem, newName: String) {
        require(newName.isNotBlank() && newName !in listOf(".", "..") &&
            '/' !in newName && '\\' !in newName && '\u0000' !in newName) { "Недопустимое имя файла" }
        if (item.name == newName) return
        val source = StorageAccess.file(context, item)
        if (source != null && source.exists()) {
            val target = File(source.parentFile, newName)
            check(!target.exists()) { "Файл $newName уже существует" }
            if (StorageFolders.hasFileAccess() || (source.canWrite() && source.path.startsWith(context.filesDir.path))) {
                check(source.renameTo(target)) { "Не удалось переименовать ${item.name}" }
                MediaScannerConnection.scanFile(context, arrayOf(source.path, target.path), null, null)
                return
            }
        }
        if (DocumentsContract.isDocumentUri(context, item.uri)) {
            val parent = StorageAccess.parent(context, item)
            check(StorageAccess.find(context, parent, newName) == null) { "Файл $newName уже существует" }
            check(DocumentsContract.renameDocument(context.contentResolver, item.uri, newName) != null) {
                "Не удалось переименовать ${item.name}"
            }
            return
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
        check(resolver.update(item.uri, values, null, null) == 1) { "Переименование ${item.name} не выполнено" }
    }
}

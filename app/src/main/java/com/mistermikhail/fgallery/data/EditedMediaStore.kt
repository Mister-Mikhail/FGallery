package com.mistermikhail.fgallery.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

class EditedMediaStore(private val context: Context) {
    private val resolver = context.contentResolver

    private fun canWriteDirect(source: MediaItem): Boolean {
        val file = StorageAccess.file(context, source) ?: return false
        val privateRoots = listOfNotNull(context.filesDir, context.cacheDir, context.getExternalFilesDir(null), context.externalCacheDir)
        val privateFile = privateRoots.any { file.path.startsWith(it.path + "/") }
        return file.canWrite() && (StorageFolders.hasFileAccess() || privateFile)
    }

    private fun copyName(source: MediaItem, extension: String, parent: String): String =
        CropCopyNames.next(source.name, extension) { candidate ->
            if (parent.startsWith("content://")) StorageAccess.find(context, parent, candidate) != null
            else File(parent, candidate).exists()
        }

    private fun copyNameInMediaStore(source: MediaItem, extension: String): String =
        CropCopyNames.next(source.name, extension) { candidate ->
            val collection = MediaStore.Files.getContentUri(source.storageId)
            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(candidate, source.relativePath), null)?.use { it.moveToFirst() } ?: false
        }

    fun saveCopy(source: MediaItem, edited: Uri, mime: String, extension: String): Uri {
        val originalParent = DriveRecycleBin(context).originalParent(source)
        if (canWriteDirect(source) && originalParent?.startsWith("content://") != true) return saveCopyFile(source, edited, extension, originalParent)
        if (originalParent != null) {
            val parent = StorageAccess.ensureDirectory(context, originalParent)
            val destination = StorageAccess.create(context, parent, copyName(source, extension, parent), mime)
            try { copy(edited, destination); CropExif.transfer(context, source, destination, mime); return destination }
            catch (e: Exception) { runCatching { StorageAccess.delete(context, destination) }; throw e }
        }
        if (android.provider.DocumentsContract.isDocumentUri(context, source.uri) || source.folderTarget.startsWith("content://")) {
            val parent = StorageAccess.parent(context, source)
            val destination = StorageAccess.create(context, parent, copyName(source, extension, parent), mime)
            try { copy(edited, destination); CropExif.transfer(context, source, destination, mime); return destination }
            catch (e: Exception) { runCatching { StorageAccess.delete(context, destination) }; throw e }
        }
        val collection = if (mime.startsWith("video/")) MediaStore.Video.Media.getContentUri(source.storageId) else MediaStore.Images.Media.getContentUri(source.storageId)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, copyNameInMediaStore(source, extension))
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, source.relativePath.ifBlank { if (mime.startsWith("video/")) "Movies/FGallery/" else "Pictures/FGallery/" })
            if (source.dateTakenMillis > 0 && mime.startsWith("image/"))
                put(MediaStore.Images.Media.DATE_TAKEN, source.dateTakenMillis)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val destination = resolver.insert(collection, values) ?: error("Не удалось создать файл")
        try {
            copy(edited, destination)
            CropExif.transfer(context, source, destination, mime)
            check(resolver.update(destination, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
            return destination
        } catch (e: Exception) {
            runCatching { resolver.delete(destination, null, null) }
            throw e
        }
    }

    /** Permission is obtained before this call. Keep a complete backup until verified. */
    fun replace(source: MediaItem, edited: Uri, mime: String, extension: String) {
        if (canWriteDirect(source)) {
            replaceFile(source, edited, extension)
            return
        }
        // Open without truncation first. If this fails, Android permission is still needed.
        resolver.openFileDescriptor(source.uri, "rw")?.use { } ?: error("Нет доступа к оригиналу")
        val backup = File.createTempFile("original_", ".backup", context.filesDir)
        var originalTouched = false
        try {
            resolver.openInputStream(source.uri)?.use { input ->
                FileOutputStream(backup).use { output -> input.copyTo(output); output.fd.sync() }
            } ?: error("Не удалось создать резервную копию")
            originalTouched = true
            copy(edited, source.uri)
            CropExif.transfer(context, source.copy(uri = Uri.fromFile(backup)), source.uri, mime)
            if (android.provider.DocumentsContract.isDocumentUri(context, source.uri)) {
                val same = source.name.substringAfterLast('.', "").lowercase().let { it == extension || (extension == "jpg" && it == "jpeg") }
                if (!same) check(android.provider.DocumentsContract.renameDocument(resolver, source.uri, "${source.name.substringBeforeLast('.')}.$extension") != null)
            } else check(resolver.update(source.uri, ContentValues().apply {
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${source.name.substringBeforeLast('.')}.$extension")
            }, null, null) == 1)
            backup.delete()
        } catch (e: Exception) {
            val restored = !originalTouched || runCatching { copy(Uri.fromFile(backup), source.uri); true }.getOrDefault(false)
            if (restored) backup.delete()
            else throw IllegalStateException("Оригинал не восстановлен. Резервная копия сохранена: ${backup.absolutePath}", e)
            throw e
        }
    }

    private fun replaceFile(source: MediaItem, edited: Uri, extension: String) {
        val path = StorageAccess.file(context, source)?.path
        val original = File(path ?: error("Не найден оригинал")).canonicalFile
        val parent = original.parentFile ?: error("Не найдена папка оригинала")
        val originalExtension = original.extension.lowercase()
        val sameFormat = originalExtension == extension || (extension == "jpg" && originalExtension == "jpeg")
        val destination = if (sameFormat) original else File(parent, "${original.nameWithoutExtension}.$extension")
        check(destination == original || !destination.exists()) { "Файл с таким именем уже существует" }
        val staged = File.createTempFile(".fgallery_edit_", ".$extension", parent)
        val backup = File.createTempFile(".fgallery_original_", ".backup", parent).also { check(it.delete()) }
        var movedOriginal = false
        var committed = false
        try {
            copy(edited, Uri.fromFile(staged))
            CropExif.transfer(context, source, Uri.fromFile(staged), if (extension in listOf("jpg", "jpeg")) "image/jpeg" else "image/png")
            check(original.renameTo(backup)) { "Не удалось сохранить резервную копию оригинала" }
            movedOriginal = true
            check(staged.renameTo(destination)) { "Не удалось записать результат" }
            committed = true
            backup.delete()
            val scanned = java.util.concurrent.CountDownLatch(2)
            android.media.MediaScannerConnection.scanFile(context, arrayOf(original.path, destination.path), null) { _, _ -> scanned.countDown() }
            scanned.await(20, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            if (movedOriginal && !committed) {
                if (!backup.renameTo(original)) throw IllegalStateException("Резервная копия сохранена: ${backup.path}", e)
            }
            throw e
        } finally {
            staged.delete()
        }
    }

    private fun saveCopyFile(source: MediaItem, edited: Uri, extension: String, originalParent: String? = null): Uri {
        val path = StorageAccess.file(context, source)?.path
        val original = File(path ?: error("Не найден исходный файл")).canonicalFile
        val parent = originalParent?.let { File(StorageAccess.ensureDirectory(context, it)) } ?: original.parentFile ?: error("Не найдена папка")
        val destination = File(parent, copyName(source, extension, parent.path))
        val staged = File.createTempFile(".fgallery_copy_", ".$extension", parent)
        try {
            copy(edited, Uri.fromFile(staged))
            CropExif.transfer(context, source, Uri.fromFile(staged), if (extension in listOf("jpg", "jpeg")) "image/jpeg" else if (extension == "png") "image/png" else "application/octet-stream")
            check(!destination.exists() && staged.renameTo(destination)) { "Не удалось сохранить копию" }
            if (source.dateModifiedMillis > 0) destination.setLastModified(source.dateModifiedMillis)
            android.media.MediaScannerConnection.scanFile(context, arrayOf(destination.path), null, null)
            return Uri.fromFile(destination)
        } finally { staged.delete() }
    }

    private fun copy(from: Uri, to: Uri) {
        // Some content providers report an inaccurate descriptor length. Verify actual bytes instead.
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val written = resolver.openInputStream(from)?.use { input ->
            resolver.openOutputStream(to, "w")?.use { output ->
                val buffer = ByteArray(64 * 1024)
                var count = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    count += n
                }
                output.flush()
                if (output is FileOutputStream) output.fd.sync()
                count
            } ?: error("Нет доступа к записи")
        } ?: error("Нет доступа к источнику")
        check(written > 0) { "Пустой результат кадрирования" }
        val verification = java.security.MessageDigest.getInstance("SHA-256")
        val verified = resolver.openInputStream(to)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            var count = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                verification.update(buffer, 0, n)
                count += n
            }
            count
        } ?: error("Не удалось проверить сохранённый файл")
        check(verified == written && verification.digest().contentEquals(digest.digest())) { "Сохранённый файл не совпадает с результатом кадрирования" }
    }
}

package com.mistermikhail.fgallery.data

import android.content.Context
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object StorageFolders {
    fun hasFileAccess(): Boolean = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

    fun roots(context: Context): List<File> = (listOf(Environment.getExternalStorageDirectory()) +
        context.getExternalFilesDirs(null).mapNotNull { it?.absolutePath?.substringBefore("/Android/")?.let(::File) }).distinctBy { it.absolutePath }

    fun primaryFolders(): List<String> {
        if (!hasFileAccess()) return emptyList()
        val root = Environment.getExternalStorageDirectory()
        return root.walkTopDown().onEnter { it.name != "Android" && !it.name.startsWith('.') }
            .filter { it.isDirectory && it != root }
            .map { it.relativeTo(root).invariantSeparatorsPath + "/" }.toList().sorted()
    }

    fun create(relativePath: String): String {
        check(hasFileAccess()) { "Для создания папки нужен доступ ко всем файлам" }
        val root = Environment.getExternalStorageDirectory().canonicalFile
        val folder = File(root, relativePath.trim().trim('/')).canonicalFile
        require(folder.path.startsWith(root.path + "/")) { "Недопустимая папка" }
        require(!folder.path.startsWith(File(root, "Android").path)) { "Папки Android защищены системой" }
        check(folder.isDirectory || folder.mkdirs()) { "Не удалось создать папку" }
        return folder.relativeTo(root).invariantSeparatorsPath + "/"
    }

    fun move(context: Context, item: MediaItem, relativePath: String) {
        val originalPath = if (item.uri.scheme == "file") item.uri.path else context.contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        val actualPath = originalPath ?: error("Не удалось определить путь ${item.name}")
        val source = File(actualPath).canonicalFile
        val root = roots(context).firstOrNull { source.path.startsWith(it.canonicalPath + "/") }?.canonicalFile
            ?: error("Файл находится вне доступного накопителя")
        val folder = File(root, relativePath).canonicalFile
        require(folder.path.startsWith(root.path + "/")) { "Недопустимая папка" }
        require(!folder.path.startsWith(File(root, "Android").path)) { "Папки Android защищены системой" }
        check(folder.isDirectory || folder.mkdirs()) { "Не удалось создать папку назначения" }
        val destination = File(folder, source.name)
        if (source == destination) return
        check(!destination.exists()) { "В целевой папке уже есть ${source.name}" }
        val before = source.length()
        check(source.renameTo(destination)) { "Не удалось переместить ${source.name}" }
        check(destination.exists() && destination.length() == before && !source.exists()) { "Перемещение не подтверждено" }
        // Wait for MediaStore to reflect the filesystem before reloading the gallery.
        val scanner = CountDownLatch(2)
        MediaScannerConnection.scanFile(context, arrayOf(source.path, destination.path), null) { _, _ -> scanner.countDown() }
        scanner.await(20, TimeUnit.SECONDS)
    }
}

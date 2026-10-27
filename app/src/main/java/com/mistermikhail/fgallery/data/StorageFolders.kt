package com.mistermikhail.fgallery.data

import android.content.Context
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.nio.file.Files

object StorageFolders {
    fun hasFileAccess(): Boolean = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

    fun roots(context: Context): List<File> = StorageAccess.mounted(context).mapNotNull { it.directory }

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

    fun move(context: Context, item: MediaItem, relativePath: String, targetName: String = item.name): Uri {
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
        if (source.parentFile?.canonicalFile == folder && source.name == targetName)
            return Uri.fromFile(source)
        val destinationName = MoveNamePlanner.plan(
            listOf(targetName), folder.list()?.toList().orEmpty()
        ).single()
        val destination = File(folder, destinationName)
        check(source != destination && !destination.exists()) { "Файл назначения уже существует" }
        val before = source.length()
        // Files.move without REPLACE_EXISTING is safe against a concurrent name
        // collision; File.renameTo may silently overwrite on Linux filesystems.
        Files.move(source.toPath(), destination.toPath())
        if (!(destination.exists() && destination.length() == before && !source.exists())) {
            runCatching { if (destination.exists() && !source.exists())
                Files.move(destination.toPath(), source.toPath()) }
            error("Перемещение не подтверждено, исходник не удалён намеренно")
        }
        // An asynchronous scan avoids blocking once per file (up to 20 s each)
        // during large 1000+ item moves. Both old and new paths are reconciled.
        MediaScannerConnection.scanFile(context, arrayOf(source.path, destination.path), null, null)
        return Uri.fromFile(destination)
    }
}

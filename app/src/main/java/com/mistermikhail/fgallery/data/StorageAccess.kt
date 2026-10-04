package com.mistermikhail.fgallery.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class StorageRoot(val id: String, val name: String, val target: String, val directory: File? = null)
data class StorageFolder(val target: String, val name: String, val storageId: String)

object StorageAccess {
    const val BIN = ".FGalleryRecycleBin"
    private const val PREFS = "fgallery_storage_roots"

    fun mounted(context: Context): List<StorageRoot> {
        val manager = context.getSystemService(StorageManager::class.java)
        val roots = manager.storageVolumes.mapNotNull { volume ->
            val file = if (Build.VERSION.SDK_INT >= 30) volume.directory else null
            if (file == null || volume.state !in listOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)) null
            else StorageRoot(if (volume.isPrimary) "external_primary" else volume.uuid.orEmpty().lowercase(),
                volume.getDescription(context), file.absolutePath, file)
        }
        return if (roots.any { it.id == "external_primary" }) roots else
            listOf(StorageRoot("external_primary", "Внутренняя память", Environment.getExternalStorageDirectory().path, Environment.getExternalStorageDirectory())) + roots
    }

    fun grant(context: Context, tree: Uri) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("trees", prefs.getStringSet("trees", emptySet()).orEmpty() + tree.toString()).apply()
    }

    fun granted(context: Context): List<StorageRoot> {
        val permissions = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("trees", emptySet()).orEmpty()
            .filter { it in permissions }.mapNotNull { text -> runCatching {
                val tree = Uri.parse(text)
                val id = DocumentsContract.getTreeDocumentId(tree)
                val volume = id.substringBefore(':').let { if (it == "primary") "external_primary" else it.lowercase() }
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                val name = mounted(context).firstOrNull { it.id == volume }?.name ?: id.substringBefore(':')
                StorageRoot(volume, name, uri.toString())
            }.getOrNull() }
    }

    fun roots(context: Context): List<StorageRoot> = mounted(context) + granted(context)

    fun file(context: Context, item: MediaItem): File? {
        if (item.sourcePath.isNotBlank()) return File(item.sourcePath)
        if (item.uri.scheme == "file") return item.uri.path?.let(::File)
        if (item.uri.authority == "media") return runCatching {
            context.contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.let(::File) else null
            }
        }.getOrNull()
        return null
    }

    fun parent(context: Context, item: MediaItem): String {
        if (item.folderTarget.isNotBlank()) return item.folderTarget
        file(context, item)?.parentFile?.let { return it.path }
        val id = DocumentsContract.getDocumentId(item.uri)
        val parent = if ('/' in id) id.substringBeforeLast('/') else id.substringBefore(':') + ":"
        return DocumentsContract.buildDocumentUriUsingTree(item.uri, parent).toString()
    }

    fun children(context: Context, target: String): List<Pair<String, String>> {
        if (!target.startsWith("content://")) return File(target).listFiles()?.map { it.name to it.path }.orEmpty()
        val uri = Uri.parse(target)
        val id = DocumentsContract.getDocumentId(uri)
        return context.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(uri, id),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(1) to DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0)).toString()) }
        }.orEmpty()
    }

    fun find(context: Context, parent: String, name: String): String? = children(context, parent).firstOrNull { it.first == name }?.second

    fun directory(context: Context, parent: String, name: String): String {
        require(name.isNotBlank() && '/' !in name && '\\' !in name && name !in listOf(".", "..")) { "Недопустимое имя папки" }
        find(context, parent, name)?.let { return it }
        if (!parent.startsWith("content://")) return File(parent, name).apply { check(isDirectory || mkdirs()) { "Нет доступа для создания папки" } }.path
        return DocumentsContract.createDocument(context.contentResolver, Uri.parse(parent), DocumentsContract.Document.MIME_TYPE_DIR, name)?.toString()
            ?: error("Не удалось создать папку")
    }

    fun create(context: Context, parent: String, name: String, mime: String): Uri {
        require(name.isNotBlank() && '/' !in name && '\\' !in name) { "Недопустимое имя файла" }
        check(find(context, parent, name) == null) { "В папке уже есть $name" }
        return if (parent.startsWith("content://")) DocumentsContract.createDocument(context.contentResolver, Uri.parse(parent), mime, name)
            ?: error("Не удалось создать файл")
        else Uri.fromFile(File(parent, name).apply { check(createNewFile()) { "Нет доступа к папке назначения" } })
    }

    fun delete(context: Context, uri: Uri): Boolean = if (uri.scheme == "file") File(uri.path!!).delete()
        else if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.deleteDocument(context.contentResolver, uri)
        else context.contentResolver.delete(uri, null, null) > 0

    fun documentForFile(context: Context, file: File): Uri? {
        val root = mounted(context).firstOrNull { file.path.startsWith(it.directory!!.path + "/") } ?: return null
        val relative = file.relativeTo(root.directory!!).invariantSeparatorsPath
        val prefix = if (root.id == "external_primary") "primary" else root.directory!!.name
        val docId = "$prefix:$relative"
        return granted(context).firstOrNull { grant ->
            grant.id == root.id && docId.startsWith(DocumentsContract.getDocumentId(Uri.parse(grant.target)).trimEnd('/') + "/")
        }?.let { DocumentsContract.buildDocumentUriUsingTree(Uri.parse(it.target), docId) }
            ?: granted(context).firstOrNull { it.id == root.id && DocumentsContract.getDocumentId(Uri.parse(it.target)).endsWith(':') }
                ?.let { DocumentsContract.buildDocumentUriUsingTree(Uri.parse(it.target), docId) }
    }

    suspend fun move(context: Context, item: MediaItem, parent: String, name: String = item.name): Uri {
        val sourceFile = file(context, item)
        val source = if (sourceFile?.exists() == true && sourceFile.canWrite()) Uri.fromFile(sourceFile)
            else sourceFile?.let { documentForFile(context, it) } ?: item.uri
        val target = if (parent.startsWith("content://")) parent else {
            val folder = File(parent)
            if (!folder.canWrite()) documentForFile(context, folder)?.toString() ?: parent else parent
        }
        if (source.scheme == "file" && !target.startsWith("content://")) {
            val destination = File(target, name)
            if (sourceFile?.canonicalFile == destination.canonicalFile) return source
            check(!destination.exists()) { "В папке уже есть $name" }
            if (File(source.path!!).renameTo(destination)) {
                MediaScannerConnection.scanFile(context, arrayOf(source.path!!, destination.path), null, null)
                return Uri.fromFile(destination)
            }
        }
        if (name == item.name && source.scheme == "content" && DocumentsContract.isDocumentUri(context, source) && target.startsWith("content://") && source.authority == Uri.parse(target).authority) {
            check(find(context, target, name) == null) { "В папке уже есть $name" }
            val originalParent = StorageAccess.parent(context, item)
            val moved = runCatching { DocumentsContract.moveDocument(context.contentResolver, source, Uri.parse(originalParent), Uri.parse(target)) }.getOrNull()
            if (moved != null) return moved
        }
        currentCoroutineContext().ensureActive()
        val destination = create(context, target, name, item.mimeType ?: "application/octet-stream")
        var sourceDeleted = false
        try {
            val expected = MessageDigest.getInstance("SHA-256")
            val written = context.contentResolver.openInputStream(source)?.use { input ->
                context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                    var count = 0L; val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer); if (n < 0) break
                        expected.update(buffer, 0, n); output.write(buffer, 0, n); count += n
                    }
                    count
                } ?: error("Нет доступа к папке назначения")
            } ?: error("Нет доступа к исходному файлу")
            val actual = MessageDigest.getInstance("SHA-256")
            val verified = context.contentResolver.openInputStream(destination)?.use { input ->
                var count = 0L; val buffer = ByteArray(65536)
                while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; actual.update(buffer, 0, n); count += n }; count
            }
            check(verified == written && expected.digest().contentEquals(actual.digest())) { "Копия не прошла проверку" }
            currentCoroutineContext().ensureActive()
            check(delete(context, source)) { "Копия сохранена, но исходник не удалён" }
            sourceDeleted = true
            MediaScannerConnection.scanFile(context, listOfNotNull(sourceFile?.path, destination.takeIf { it.scheme == "file" }?.path).toTypedArray(), null, null)
            return destination
        } finally {
            if (!sourceDeleted) runCatching { delete(context, destination) }
        }
    }

    fun stableId(value: String): Long { var hash = 1125899906842597L; value.forEach { hash = 31 * hash + it.code }; return hash }
}

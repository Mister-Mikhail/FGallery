package com.mistermikhail.fgallery.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** Also discovers volumes which Android has not indexed in MediaStore yet. */
object DriveLibrary {
    fun kind(name: String, mime: String?): MediaKind? = when {
        name.endsWith(".pdf", true) || mime == "application/pdf" -> MediaKind.PDF
        name.endsWith(".svg", true) || mime == "image/svg+xml" -> MediaKind.SVG
        name.substringAfterLast('.', "").lowercase() in setOf("dng", "cr2", "cr3", "nef", "nrw", "arw", "sr2", "srf", "orf", "rw2", "raf", "pef", "raw", "3fr", "fff", "iiq") -> MediaKind.RAW
        mime?.startsWith("video/") == true -> MediaKind.VIDEO
        mime?.startsWith("image/") == true || TiffImages.isTiff(name) -> MediaKind.IMAGE
        else -> null
    }
    fun mime(name: String): String? = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
    fun item(uri: Uri, name: String, mime: String?, size: Long, modified: Long, root: StorageRoot, parent: String, relative: String = ""): MediaItem? {
        val kind = kind(name, mime) ?: return null
        val folder = if (parent.startsWith("content://")) relative.trimEnd('/').substringAfterLast('/').ifBlank { root.name } else File(parent).name
        return MediaItem(StorageAccess.stableId(uri.toString()), uri, name, mime, kind, modified, 0, 0, 0,
            if (root.id == "external_primary") folder else "${root.name} · $folder", relative, size, modified,
            root.id, root.name, parent, if (uri.scheme == "file") uri.path.orEmpty() else "")
    }
    suspend fun load(context: Context, indexed: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        val known = indexed.mapNotNull { StorageAccess.file(context, it)?.path }.toHashSet()
        val result = mutableListOf<MediaItem>()
        if (StorageFolders.hasFileAccess()) for (root in StorageAccess.mounted(context)) {
            root.directory?.walkTopDown()?.onEnter { it.name != "Android" && !it.name.startsWith('.') }?.forEach { file ->
                currentCoroutineContext().ensureActive()
                if (file.isFile && file.path !in known) {
                    item(Uri.fromFile(file), file.name, mime(file.name), file.length(), file.lastModified(), root, file.parent.orEmpty(),
                        file.parentFile?.relativeTo(root.directory).toString().trim('/').let { if (it == ".") "" else "$it/" })?.let(result::add)
                }
            }
        }
        for (root in StorageAccess.granted(context)) {
            suspend fun visit(target: String, relative: String) {
                currentCoroutineContext().ensureActive()
                val uri = Uri.parse(target)
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))
                val rows = mutableListOf<Triple<String, String, String>>()
                context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(1).orEmpty(); if (name.startsWith('.') || name == "Android") continue
                        val id = c.getString(0); val mime = c.getString(2)
                        val child = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                        val mounted = StorageAccess.mounted(context).firstOrNull { it.id == root.id }?.directory
                        val path = mounted?.let { File(it, id.substringAfter(':', "")).path }
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) rows.add(Triple(child.toString(), name, mime))
                        else if (path == null || path !in known) item(child, name, mime, c.getLong(3), c.getLong(4), root, target, relative)?.let { item ->
                            if (path == null || result.none { it.sourcePath == path }) result.add(item.copy(sourcePath = path.orEmpty()))
                        }
                    }
                }
                for ((child, name, _) in rows) visit(child, "$relative$name/")
            }
            // A disconnected root stays registered; it is skipped until the drive returns.
            try { visit(root.target, "") } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
        }
        result
    }

    suspend fun folders(context: Context): List<StorageFolder> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StorageFolder>()
        for (root in StorageAccess.roots(context)) {
            if (root.directory != null) {
                if (!StorageFolders.hasFileAccess()) continue
                root.directory.walkTopDown().onEnter { it.name != "Android" && !it.name.startsWith('.') }.filter { it.isDirectory }.forEach {
                    currentCoroutineContext().ensureActive()
                    result.add(StorageFolder(it.path, if (it == root.directory) root.name else "${root.name} · ${it.relativeTo(root.directory)}", root.id))
                }
            } else {
                suspend fun visit(target: String, relative: String) {
                    currentCoroutineContext().ensureActive()
                    val uri = Uri.parse(target)
                    if (!androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)!!.exists()) return
                    result.add(StorageFolder(target, if (relative.isEmpty()) root.name else "${root.name} · $relative", root.id))
                    for ((name, child) in StorageAccess.children(context, target)) {
                        if (name.startsWith('.') || name == "Android") continue
                        if (context.contentResolver.getType(Uri.parse(child)) == DocumentsContract.Document.MIME_TYPE_DIR) visit(child, "$relative$name/")
                    }
                }
                try { visit(root.target, "") } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
            }
        }
        result.distinctBy { it.target }
    }
}

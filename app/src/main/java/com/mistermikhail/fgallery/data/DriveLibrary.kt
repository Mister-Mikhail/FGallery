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
    suspend fun load(
        context: Context, indexed: List<MediaItem>,
        priorFiles: List<MediaItem> = emptyList(),
        knownFolders: List<CatalogFolderRow> = emptyList(),
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val known = indexed.mapNotNull { StorageAccess.file(context, it)?.path }.toHashSet()
        val result = mutableListOf<MediaItem>()
        val previous = priorFiles.filter { it.uri.scheme == "file" && it.sourcePath.isNotBlank() }
            .groupBy { it.folderTarget }
        val previousFolders = knownFolders.associateBy { it.key }
        if (StorageFolders.hasFileAccess()) for (root in StorageAccess.mounted(context)) {
            val directory = root.directory ?: continue
            suspend fun visit(dir: File) {
                currentCoroutineContext().ensureActive()
                if (!dir.isDirectory || dir.name.startsWith('.') || dir.name == "Android") return
                val entries = dir.listFiles() ?: return
                val folderKey = "${root.id}/${dir.absolutePath}"
                // Folder timestamps are hints only; other apps may overwrite
                // same-name files without changing them. A full-scan safety
                // pass runs periodically and on explicit user refresh.
                val cached = previousFolders[folderKey]
                if (cached != null && cached.lastModifiedMillis > 0 &&
                    cached.lastModifiedMillis == dir.lastModified()) {
                    result.addAll(previous[dir.absolutePath].orEmpty().filter {
                        it.sourcePath !in known && File(it.sourcePath).isFile
                    })
                } else {
                    val rel = dir.relativeTo(directory).invariantSeparatorsPath
                        .let { if (it == ".") "" else "$it/" }
                    for (file in entries) {
                        currentCoroutineContext().ensureActive()
                        if (file.isFile && file.path !in known) {
                            item(Uri.fromFile(file), file.name, mime(file.name),
                                file.length(), file.lastModified(), root, dir.path, rel)?.let(result::add)
                        }
                    }
                }
                // ALWAYS traverse child directories, even when parent metadata
                // did not change (FAT/exFAT timestamps are not reliable).
                for (child in entries) {
                    if (child.isDirectory) visit(child)
                }
            }
            visit(directory)
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

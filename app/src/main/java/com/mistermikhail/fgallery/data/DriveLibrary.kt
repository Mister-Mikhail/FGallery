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
        mime?.startsWith("video/") == true ||
            name.substringAfterLast('.', "").lowercase() in setOf("mp4", "m4v", "mov", "mkv", "webm", "3gp", "avi") -> MediaKind.VIDEO
        mime?.startsWith("image/") == true || TiffImages.isTiff(name) ||
            name.substringAfterLast('.', "").lowercase() in setOf(
                "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "bmp",
            ) -> MediaKind.IMAGE
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
    /**
     * A folder's mtime is only a hint (notably on FAT/exFAT). Even when it is
     * unchanged, inspect the already-enumerated immediate children: a new file,
     * rename or overwrite can otherwise be missing until the daily full scan.
     * Reuse unchanged catalogue rows without opening or decoding media bytes.
     */
    internal fun reconcileUnchangedFolder(
        entries: Array<File>,
        known: Set<String>,
        prior: List<MediaItem>,
        root: StorageRoot,
        dir: File,
    ): List<MediaItem> {
        val cached = prior.associateBy { it.sourcePath }
        val rel = dir.relativeTo(root.directory ?: dir).invariantSeparatorsPath
            .let { if (it.isBlank() || it == ".") "" else "$it/" }
        return entries.mapNotNull { file ->
            if (!file.isFile || file.path in known) return@mapNotNull null
            val old = cached[file.path]
            val size = file.length()
            val modified = file.lastModified()
            if (old != null && old.name == file.name &&
                old.sizeBytes == size && old.dateModifiedMillis == modified) old
            else item(Uri.fromFile(file), file.name, mime(file.name),
                size, modified, root, dir.path, rel)
        }
    }

    internal suspend fun load(
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
                // An unreadable mounted folder is a failed scan, not an empty one.
                val entries = dir.listFiles() ?: error("Не удалось прочитать папку: ${dir.path}")
                val folderKey = "${root.id}/${dir.absolutePath}"
                // Folder timestamps are hints only; other apps may overwrite
                // same-name files without changing them. A full-scan safety
                // pass runs periodically and on explicit user refresh.
                val cached = previousFolders[folderKey]
                if (cached != null && cached.lastModifiedMillis > 0 &&
                    cached.lastModifiedMillis == dir.lastModified()) {
                    result.addAll(reconcileUnchangedFolder(
                        entries, known, previous[dir.absolutePath].orEmpty(), root, dir,
                    ))
                } else {
                    val rel = dir.relativeTo(directory).invariantSeparatorsPath
                        .let { if (it.isBlank() || it == ".") "" else "$it/" }
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
            // Resolve the physical mount once per SAF root, not once per file.
            // Large galleries can have thousands of document rows.
            val mountedVolume = StorageAccess.mounted(context).firstOrNull { it.id == root.id }
            val mountedRoot = mountedVolume?.directory
            suspend fun visit(target: String, relative: String) {
                currentCoroutineContext().ensureActive()
                val uri = Uri.parse(target)
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))
                val rows = mutableListOf<Triple<String, String, String>>()
                // Null cursor means provider failure, never an empty folder.
                val cursor = context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)
                    ?: error("SAF не вернул список файлов: $target")
                cursor.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(1).orEmpty(); if (name.startsWith('.') || name == "Android") continue
                        val id = c.getString(0); val mime = c.getString(2)
                        val child = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                        val path = mountedRoot?.let { File(it, id.substringAfter(':', "")).path }
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) rows.add(Triple(child.toString(), name, mime))
                        else if (path == null || path !in known) item(child, name, mime, c.getLong(3), c.getLong(4), root, target, relative)?.let { item ->
                            if (path == null || result.none { it.sourcePath == path }) result.add(item.copy(sourcePath = path.orEmpty()))
                        }
                    }
                }
                for ((child, name, _) in rows) visit(child, "$relative$name/")
            }
            // A disconnected root stays registered; it is skipped until the drive returns.
            try { visit(root.target, "") }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                // Persisted SAF grants survive USB removal. An offline volume
                // keeps its Room rows; a mounted provider failure aborts the
                // scan instead of deleting its catalogue records.
                if (mountedVolume != null) throw e
            }
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

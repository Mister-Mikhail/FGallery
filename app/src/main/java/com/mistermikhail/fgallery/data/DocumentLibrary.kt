package com.mistermikhail.fgallery.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentLibrary(private val context: Context) {
    private val preferences = context.getSharedPreferences("fgallery_documents", Context.MODE_PRIVATE)

    fun add(uris: List<Uri>) {
        val values = preferences.getStringSet("uris", emptySet()).orEmpty() + uris.map(Uri::toString)
        preferences.edit().putStringSet("uris", values).apply()
    }

    suspend fun load(): List<MediaItem> = withContext(Dispatchers.IO) {
        val picked = preferences.getStringSet("uris", emptySet()).orEmpty()
        picked.mapNotNull { value ->
            runCatching {
                val uri = Uri.parse(value)
                val local = if (uri.scheme == "file") File(uri.path!!) else null
                if (local != null && !local.exists()) return@runCatching null
                if (local == null && DocumentsContract.isDocumentUri(context, uri) && DocumentFile.fromSingleUri(context, uri)?.exists() != true) return@runCatching null
                val mime = if (local != null) { when { local.extension.equals("pdf", true) -> "application/pdf"; TiffImages.isTiff(local.name) -> "image/tiff"; else -> "image/svg+xml" } } else context.contentResolver.getType(uri)
                var name = local?.name ?: uri.lastPathSegment.orEmpty()
                var size = local?.length() ?: 0L
                if (local == null) context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0).orEmpty()
                        if (!cursor.isNull(1)) size = cursor.getLong(1)
                    }
                }
                val kind = when {
                    mime == "application/pdf" || name.endsWith(".pdf", true) -> MediaKind.PDF
                    mime == "image/svg+xml" || name.endsWith(".svg", true) -> MediaKind.SVG
                    TiffImages.isTiff(name, mime) -> MediaKind.IMAGE
                    else -> return@runCatching null
                }
                val modified = local?.lastModified() ?: DocumentFile.fromSingleUri(context, uri)?.lastModified() ?: 0L
                val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrDefault("")
                val folder = if (local != null) StorageFolders.roots(context).firstOrNull { local.path.startsWith(it.path + "/") }?.let { local.parentFile?.relativeTo(it)?.invariantSeparatorsPath }.orEmpty() else documentId.substringAfter(':', "").substringBeforeLast('/', "")
                val documentVolume = documentId.substringBefore(':').let { if (it == "primary") "external_primary" else it.lowercase() }
                val root = StorageAccess.mounted(context).firstOrNull { (local != null && local.path.startsWith(it.directory!!.path + "/")) || (local == null && it.id == documentVolume) }
                MediaItem(
                    id = stableDocumentId(value), uri = uri, name = name, mimeType = mime,
                    kind = kind, dateTakenMillis = modified, width = 0, height = 0,
                    durationMillis = 0, album = folder.substringAfterLast('/').ifBlank { "Документы" }.let { name -> if (root != null && root.id != "external_primary") "${root.name} · $name" else name },
                    relativePath = folder.takeIf { it.isNotBlank() }?.plus("/").orEmpty(),
                    sizeBytes = size, dateModifiedMillis = modified,
                    storageId = root?.id ?: documentVolume.ifBlank { "external_primary" }, storageName = root?.name ?: "Документы",
                    sourcePath = local?.path.orEmpty(), folderTarget = local?.parent.orEmpty(),
                )
            }.getOrNull()
        }
    }

    private fun stableDocumentId(value: String): Long {
        var hash = 1125899906842597L
        value.forEach { hash = 31 * hash + it.code }
        return hash or Long.MIN_VALUE
    }
}

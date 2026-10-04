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
        val discovered = if (StorageFolders.hasFileAccess()) StorageFolders.roots(context).flatMap { root ->
            root.walkTopDown().onEnter { it.name != "Android" && !it.name.startsWith('.') }
                .filter { it.isFile && (it.extension.equals("pdf", true) || it.extension.equals("svg", true)) }
                .map { Uri.fromFile(it).toString() }.toList()
        } else emptyList()
        (picked + discovered).mapNotNull { value ->
            runCatching {
                val uri = Uri.parse(value)
                val local = if (uri.scheme == "file") File(uri.path!!) else null
                val mime = if (local != null) { if (local.extension.equals("pdf", true)) "application/pdf" else "image/svg+xml" } else context.contentResolver.getType(uri)
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
                    else -> return@runCatching null
                }
                val modified = local?.lastModified() ?: DocumentFile.fromSingleUri(context, uri)?.lastModified() ?: 0L
                val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrDefault("")
                val folder = if (local != null) StorageFolders.roots(context).firstOrNull { local.path.startsWith(it.path + "/") }?.let { local.parentFile?.relativeTo(it)?.invariantSeparatorsPath }.orEmpty() else documentId.substringAfter(':', "").substringBeforeLast('/', "")
                MediaItem(
                    id = stableDocumentId(value), uri = uri, name = name, mimeType = mime,
                    kind = kind, dateTakenMillis = modified, width = 0, height = 0,
                    durationMillis = 0, album = folder.substringAfterLast('/').ifBlank { "Документы" },
                    relativePath = folder.takeIf { it.isNotBlank() }?.plus("/").orEmpty(),
                    sizeBytes = size, dateModifiedMillis = modified,
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

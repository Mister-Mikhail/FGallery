package com.mistermikhail.fgallery

import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File

/** A document-only drive: exercising the same API used by removable-volume SAF grants. */
class TestDriveProvider : DocumentsProvider() {
    private val directory get() = File(context!!.cacheDir, "document-drive").apply { mkdirs() }
    private fun file(id: String): File {
        val child = if (id == "root") directory else File(directory, id.removePrefix("root/"))
        require(child.canonicalPath == directory.canonicalPath || child.canonicalPath.startsWith(directory.canonicalPath + "/"))
        return child
    }
    private fun id(file: File) = if (file == directory) "root" else "root/" + file.relativeTo(directory).invariantSeparatorsPath
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?): MatrixCursor = MatrixCursor(arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID, DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS)).apply {
        addRow(arrayOf("root", "root", "Test drive", DocumentsContract.Root.FLAG_SUPPORTS_CREATE))
    }
    private fun cursor(projection: Array<out String>?) = MatrixCursor(projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_FLAGS))
    private fun add(cursor: MatrixCursor, file: File) {
        if (!file.exists()) return
        val values = mapOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID to id(file), DocumentsContract.Document.COLUMN_DISPLAY_NAME to file.name,
            DocumentsContract.Document.COLUMN_MIME_TYPE to if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "image/jpeg",
            DocumentsContract.Document.COLUMN_SIZE to file.length(), DocumentsContract.Document.COLUMN_LAST_MODIFIED to file.lastModified(),
            DocumentsContract.Document.COLUMN_FLAGS to (DocumentsContract.Document.FLAG_SUPPORTS_DELETE or DocumentsContract.Document.FLAG_SUPPORTS_WRITE or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or DocumentsContract.Document.FLAG_SUPPORTS_MOVE))
        cursor.addRow(cursor.columnNames.map { values[it] }.toTypedArray())
    }
    override fun queryDocument(documentId: String, projection: Array<out String>?) = cursor(projection).apply { add(this, file(documentId)) }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) = cursor(projection).apply {
        file(parentDocumentId).listFiles()?.forEach { add(this, it) }
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val destination = File(file(parentDocumentId), displayName)
        check(!destination.exists())
        check(if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) destination.mkdir() else destination.createNewFile())
        return id(destination)
    }
    override fun deleteDocument(documentId: String) { check(file(documentId).delete()) }
    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String {
        val source = file(sourceDocumentId); val target = File(file(targetParentDocumentId), source.name)
        check(!target.exists() && source.renameTo(target)); return id(target)
    }
    override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId.startsWith("$parentDocumentId/")
}

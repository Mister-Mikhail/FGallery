package com.mistermikhail.fgallery.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns

/**
 * Provider-backed Stage 6 fingerprints. No temporary RAW copies, no writes.
 * A provider without a stable mtime/size or a seekable descriptor is skipped:
 * a false negative is safer than proposing a false duplicate.
 */
internal class ContentFingerprintSource(private val context: Context) {
    private val resolver = context.contentResolver

    data class Signature(val size: Long, val modified: Long)

    fun signature(uri: Uri): Signature? {
        val modifiedColumn = if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        } else {
            MediaStore.MediaColumns.DATE_MODIFIED
        }
        return resolver.query(uri, arrayOf(OpenableColumns.SIZE, modifiedColumn), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val modifiedIndex = cursor.getColumnIndex(modifiedColumn)
                if (sizeIndex < 0 || modifiedIndex < 0 ||
                    cursor.isNull(sizeIndex) || cursor.isNull(modifiedIndex)
                ) return@use null
                Signature(cursor.getLong(sizeIndex), cursor.getLong(modifiedIndex))
                    .takeIf { it.size > 0L && it.modified > 0L }
            }
    }

    fun quick(uri: Uri, expectedSize: Long, checkActive: () -> Unit): String? {
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: return null
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            // Pipes and cloud-backed streams cannot safely seek to the tail.
            if (descriptor.statSize >= 0L && descriptor.statSize != expectedSize) return@use null
            val channel = input.channel
            if (channel.size() != expectedSize) return@use null
            DuplicateFingerprints.quick(channel, checkActive)
        }
    }

    fun full(uri: Uri, expectedSize: Long, checkActive: () -> Unit): String? =
        resolver.openInputStream(uri)?.use { stream ->
            DuplicateFingerprints.full(stream, checkActive, expectedSize)
        }
}

package com.mistermikhail.fgallery.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Reads the CURRENT bytes of a Stage 6 candidate rather than trusting the
 * catalogue's stored digest. Invoke on Dispatchers.IO, never on the UI thread.
 *
 * This is a verification primitive, not authorization to move a file. The
 * caller must also check the current catalogue, explicit user consent and
 * the recycle-bin preflight before mutating any source.
 */
internal fun currentDuplicateSha256(
    context: Context,
    row: CatalogMediaRow,
    checkActive: () -> Unit = {},
): String {
    require(row.sizeBytes > 0L) { "Cannot verify an unknown/empty source length" }
    checkActive()
    val uri = Uri.parse(row.uri)
    val stream = context.contentResolver.openInputStream(uri)
        ?: throw IOException("Cannot open original media for duplicate verification")
    return stream.use {
        DuplicateFingerprints.full(it, checkActive, expectedBytes = row.sizeBytes)
    }
}

/** Verify the source a manual comparison targets before consent and again at the move boundary. */
internal fun comparisonTrashSha256(
    context: Context,
    row: CatalogMediaRow,
    item: MediaItem,
    currentRows: List<CatalogMediaRow>,
    checkActive: () -> Unit = {},
): String {
    val current = currentRows.singleOrNull { it.uri == row.uri }
    val stale = "Исходный файл изменился или недоступен. Обновите поиск."
    check(current != null && !current.recycled && current.kind == MediaKind.IMAGE.name &&
        current.mediaId == row.mediaId && current.name == row.name && current.storageId == row.storageId &&
        current.sourcePath == row.sourcePath && current.folderTarget == row.folderTarget &&
        current.dateModifiedMillis == row.dateModifiedMillis && current.sizeBytes == row.sizeBytes &&
        row.sizeBytes > 0L) { stale }

    val sameSize = currentRows.filter { it.sizeBytes == row.sizeBytes && !it.recycled }
    val path = StorageAccess.file(context, item)?.canonicalFile
    if (path != null) {
        if (item.uri.scheme == "file") check(item.uri.path?.let { File(it).canonicalFile } == path) { stale }
        check(sameSize.none { other ->
            other.uri != row.uri && other.sourcePath.isNotBlank() && File(other.sourcePath).canonicalFile == path
        }) { "Файл представлен в каталоге по нескольким адресам. Обновите поиск." }
    }
    val digest = currentDuplicateSha256(context, row, checkActive)
    if (path?.isFile == true && item.uri.scheme != "file") {
        check(path.inputStream().use { DuplicateFingerprints.full(it, checkActive, row.sizeBytes) } == digest) { stale }
    }
    val indexed = current.fullHash?.lowercase(Locale.ROOT)
    check(indexed == null || indexed == digest) { stale }
    val copies = sameSize.filter { it.uri != row.uri && it.fullHash.equals(digest, ignoreCase = true) }
    check(copies.isEmpty() || exactDuplicateGroups(copies + current.copy(fullHash = digest)).none { it.first().uri == row.uri }) {
        "Этот снимок помечен «Сохранить» в точных копиях."
    }
    return digest
}

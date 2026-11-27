package com.mistermikhail.fgallery.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One physical bin on each volume. A local journal keeps restore information across unplugging. */
class DriveRecycleBin(private val context: Context, private val mounted: () -> List<StorageRoot> = { StorageAccess.mounted(context) }, private val granted: () -> List<StorageRoot> = { StorageAccess.granted(context) }) {
    private val journal = RecycleBinJournal(context)

    private fun metadata(item: MediaItem, token: String, folder: String, parent: String) = JSONObject().apply {
        put("token", token); put("bin", folder); put("parent", parent); put("name", item.name); put("mime", item.mimeType)
        put("kind", item.kind.name); put("storage", item.storageId); put("storageName", item.storageName)
        put("album", item.album); put("relative", item.relativePath); put("modified", item.dateModifiedMillis)
        put("date", item.dateTakenMillis); put("width", item.width); put("height", item.height); put("duration", item.durationMillis)
        put("size", item.sizeBytes); put("original", item.uri.toString())
    }
    private inner class BinContents {
        private val folders = mutableMapOf<String, Map<String, String>>()

        fun entries(folder: String): Map<String, String> = folders.getOrPut(folder) {
            StorageAccess.children(context, folder).toMap()
        }

        private val byToken = mutableMapOf<String, Map<String, String>>()

        fun uri(record: JSONObject): Uri? = runCatching {
            val token = record.getString("token")
            val folder = record.getString("bin")
            val entries = entries(folder)
            val exact = entries[token + "_" + record.getString("name")]
            val path = exact ?: byToken.getOrPut(folder) {
                entries.entries.mapNotNull { (name, path) ->
                    name.substringBefore('_').takeIf { '_' in name && RecycleBinJournal.validToken(it) }
                        ?.let { it to path }
                }.toMap()
            }[token]
            path?.let(::storageUri)
        }.getOrNull()
    }

    private fun storageUri(path: String): Uri =
        if (path.startsWith("content://")) Uri.parse(path) else Uri.fromFile(File(path))

    private fun item(record: JSONObject, uri: Uri) = MediaItem(StorageAccess.stableId(uri.toString()), uri, record.getString("name"),
        record.optString("mime").takeUnless { it.isBlank() || it == "null" }, MediaKind.valueOf(record.getString("kind")), record.optLong("date"),
        record.optInt("width"), record.optInt("height"), record.optLong("duration"), record.getString("album"), record.optString("relative"),
        record.optLong("size"), record.optLong("modified"), record.getString("storage"), record.getString("storageName"), record.getString("bin"),
        if (uri.scheme == "file") uri.path.orEmpty() else "")

    fun originalParent(item: MediaItem): String? = journal.records().values.firstOrNull {
        it.optString("lastUri") == item.uriKey
    }?.getString("parent")

    suspend fun load(): List<MediaItem> = withContext(Dispatchers.IO) {
        lock.withLock {
            val contents = BinContents()
            val saved = journal.records()
            val records = saved.toMutableMap()
            val bins = (mounted() + granted()).mapNotNull { root ->
                runCatching { StorageAccess.find(context, root.target, StorageAccess.BIN) }.getOrNull()
            }.distinct()
            // Read the sidecars as a fallback for entries missing from the
            // phone's journal, including bins discovered after reinstall.
            for (bin in bins) {
                currentCoroutineContext().ensureActive()
                for ((name, path) in contents.entries(bin)) {
                    if (!name.endsWith(".fgallery.json")) continue
                    val token = name.removeSuffix(".fgallery.json")
                    if (!RecycleBinJournal.validToken(token)) continue
                    val existing = records[token]
                    if (existing?.optString("bin") == bin) continue
                    val record = runCatching {
                        context.contentResolver.openInputStream(storageUri(path))?.bufferedReader()
                            ?.use { JSONObject(it.readText()) }
                    }.getOrNull() ?: continue
                    if (record.optString("token") == token) records[token] = record.put("bin", bin)
                }
            }
            records.mapNotNull { (token, record) ->
                currentCoroutineContext().ensureActive()
                val found = contents.uri(record) ?: return@mapNotNull null
                val before = saved[token]?.toString()
                val local = if (found.scheme == "file") File(found.path!!) else null
                val document = if (local == null) androidx.documentfile.provider.DocumentFile.fromSingleUri(context, found) else null
                val prefix = "${token}_"
                val name = (local?.name ?: document?.name)?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
                if (name != null && name != record.optString("name")) {
                    record.put("name", name)
                    DriveLibrary.mime(name)?.let { record.put("mime", it) }
                }
                record.put("size", local?.length() ?: document?.length() ?: record.optLong("size"))
                record.put("modified", local?.lastModified() ?: document?.lastModified() ?: record.optLong("modified"))
                record.put("lastUri", found.toString())
                if (record.toString() != before) journal.write(record)
                item(record, found)
            }
        }
    }
    suspend fun trash(items: List<MediaItem>): List<String> = trashWithPreMoveCheck(items) { true }.map { it.uriKey }

    /**
     * Stage 6 mutation boundary. The verifier runs under the recycle-bin mutex
     * immediately before EACH physical move. It must re-read source bytes and
     * check the protected original and selected copy. False aborts the batch.
     * The callback must not recursively call the recycle bin (same mutex).
     */
    suspend fun trashWithPreMoveCheck(
        items: List<MediaItem>,
        verifyPreflight: (suspend (MediaItem) -> Boolean)? = null,
        onMoved: (source: MediaItem, destination: MediaItem) -> Unit = { _, _ -> },
        reconcilePaths: (Set<String>) -> Unit = ::reconcileAndroidIndex,
        verifyBeforeMove: suspend (MediaItem) -> Boolean,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val removedPaths = linkedSetOf<String>()
        try {
            lock.withLock {
                // Fail the entire proposed batch before the first mutation if any
                // selected file is already missing, changed or inaccessible.
                // The per-item check below is still required because SAF/MediaStore
                // content may change between this preflight and the actual move.
                check(preflightDuplicateTrash(items, verifyPreflight ?: verifyBeforeMove, { it.uriKey }, { item ->
                    item.sourcePath.takeIf { it.isNotBlank() }
                })) {
                    "Повторная проверка выбранных файлов не пройдена"
                }
                val binFolders = mutableMapOf<String, String>()
                val mountedRoots by lazy(mounted)
                val grantedRoots by lazy(granted)
                items.map { item ->
                    val source = StorageAccess.file(context, item)
                    val root = mountedRoots.firstOrNull { source != null && it.directory?.let { dir -> source.path.startsWith(dir.path + "/") } == true }
                    val localTarget = root?.directory?.takeIf { it.canWrite() }?.path
                    val grant = if (localTarget == null) grantedRoots.filter { it.id == item.storageId }.firstOrNull { candidate ->
                        runCatching {
                            val base = android.provider.DocumentsContract.getDocumentId(Uri.parse(candidate.target)).trimEnd('/')
                            val sourceId = if (source != null && root != null) {
                                val prefix = if (root.id == "external_primary") "primary" else root.directory!!.name
                                "$prefix:${source.relativeTo(root.directory!!).invariantSeparatorsPath}"
                            } else android.provider.DocumentsContract.getDocumentId(item.uri)
                            sourceId == base || sourceId.startsWith("$base/") || (base.endsWith(':') && sourceId.startsWith(base))
                        }.getOrDefault(false)
                    } else null
                    val target = localTarget ?: grant?.target ?: error("Нет доступа к накопителю ${item.storageName}")
                    val folder = binFolders.getOrPut(target) { StorageAccess.directory(context, target, StorageAccess.BIN) }
                    val token = UUID.randomUUID().toString()
                    val name = "${token}_${item.name}"
                    val localBin = folder.takeUnless { it.startsWith("content://") }?.let(::File)
                    val record = metadata(item, token, folder, StorageAccess.parent(context, item))
                    localBin?.let { record.put("lastUri", Uri.fromFile(File(it, name)).toString()) }
                    journal.write(record) // Recovery information must exist before moving.
                    val sidecarName = "$token.fgallery.json"
                    var sidecar: Uri? = null
                    var destinationWritten = false
                    try {
                        val newSidecar = if (localBin != null) {
                            val file = File(localBin, sidecarName)
                            check(file.createNewFile()) { "Данные восстановления уже существуют" }
                            Uri.fromFile(file)
                        } else StorageAccess.create(context, folder, sidecarName, "application/json")
                        sidecar = newSidecar
                        requireNotNull(context.contentResolver.openOutputStream(newSidecar, "w")).use { output ->
                            output.write(record.toString().toByteArray(Charsets.UTF_8))
                            output.flush()
                            if (output is java.io.FileOutputStream) output.fd.sync()
                        }
                        check(verifyBeforeMove(item)) {
                            "Повторная проверка SHA-256 не пройдена: ${item.name}"
                        }
                        currentCoroutineContext().ensureActive()
                        val moved = StorageAccess.move(
                            context, item, folder, name, scanMedia = false, uniqueLocalName = true,
                        )
                        destinationWritten = true
                        source?.path?.let(removedPaths::add)
                        val movedItem = item(record, moved)
                        onMoved(item, movedItem)
                        if (record.optString("lastUri") != moved.toString()) {
                            journal.write(record.apply { put("lastUri", moved.toString()) })
                        }
                        movedItem
                    } catch (e: Exception) {
                        if (!destinationWritten && BinContents().uri(record) == null) {
                            sidecar?.let { runCatching { StorageAccess.delete(context, it) } }
                            journal.forget(token)
                        }
                        throw e
                    }
                }
            }
        } finally {
            // Reconcile the old paths in Android's index in one request. The hidden bin
            // is tracked by our journal and Room, so it never needs media indexing.
            if (removedPaths.isNotEmpty()) reconcilePaths(removedPaths)
        }
    }

    internal fun reconcileAndroidIndex(paths: Set<String>) {
        if (paths.isNotEmpty()) runCatching {
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
        }
    }

    suspend fun restore(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        lock.withLock {
            val contents = BinContents()
            val records = journal.records().values
            for (item in items) {
                val record = records.firstOrNull { contents.uri(it) == item.uri }
                if (record == null) continue // Legacy virtual bin is handled by settings.
                val parent = StorageAccess.ensureDirectory(context, record.getString("parent"))
                StorageAccess.move(context, item.copy(name = "${record.getString("token")}_${item.name}"), parent, record.getString("name"))
                removeMetadata(record)
            }
        }
    }
    suspend fun deleted(uris: Collection<String>) = withContext(Dispatchers.IO) {
        lock.withLock {
            val contents = BinContents()
            journal.records().values.filter { it.optString("lastUri") in uris || contents.uri(it)?.toString() in uris }
                .forEach(::removeMetadata)
        }
    }
    private fun removeMetadata(record: JSONObject) {
        runCatching {
            StorageAccess.find(context, record.getString("bin"), record.getString("token") + ".fgallery.json")?.let {
                StorageAccess.delete(context, if (it.startsWith("content://")) Uri.parse(it) else Uri.fromFile(File(it)))
            }
        }
        journal.forget(record.getString("token"))
    }
    companion object { private val lock = Mutex() }
}

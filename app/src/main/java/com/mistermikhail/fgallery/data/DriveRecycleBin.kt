package com.mistermikhail.fgallery.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One physical bin on each volume. A local journal keeps restore information across unplugging. */
class DriveRecycleBin(private val context: Context, private val mounted: () -> List<StorageRoot> = { StorageAccess.mounted(context) }, private val granted: () -> List<StorageRoot> = { StorageAccess.granted(context) }) {
    private val journal = context.getSharedPreferences("fgallery_drive_bin_v1", Context.MODE_PRIVATE)
    private fun records(): List<JSONObject> = journal.all.values.filterIsInstance<String>().mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
    private fun write(record: JSONObject) { check(journal.edit().putString(record.getString("token"), record.toString()).commit()) { "Не удалось сохранить данные восстановления" } }
    private fun forget(token: String) { journal.edit().remove(token).commit() }
    private fun metadata(item: MediaItem, token: String, folder: String, parent: String) = JSONObject().apply {
        put("token", token); put("bin", folder); put("parent", parent); put("name", item.name); put("mime", item.mimeType)
        put("kind", item.kind.name); put("storage", item.storageId); put("storageName", item.storageName)
        put("album", item.album); put("relative", item.relativePath); put("modified", item.dateModifiedMillis)
        put("date", item.dateTakenMillis); put("width", item.width); put("height", item.height); put("duration", item.durationMillis)
        put("size", item.sizeBytes); put("original", item.uri.toString())
    }
    private fun uri(record: JSONObject): Uri? = runCatching {
        val token = record.getString("token")
        val entries = StorageAccess.children(context, record.getString("bin"))
        val found = entries.firstOrNull { it.first == token + "_" + record.getString("name") }
            ?: entries.firstOrNull { it.first.startsWith(token + "_") }
        found?.second?.let { if (it.startsWith("content://")) Uri.parse(it) else Uri.fromFile(File(it)) }
    }.getOrNull()
    private fun item(record: JSONObject, uri: Uri) = MediaItem(StorageAccess.stableId(uri.toString()), uri, record.getString("name"),
        record.optString("mime").takeUnless { it.isBlank() || it == "null" }, MediaKind.valueOf(record.getString("kind")), record.optLong("date"),
        record.optInt("width"), record.optInt("height"), record.optLong("duration"), record.getString("album"), record.optString("relative"),
        record.optLong("size"), record.optLong("modified"), record.getString("storage"), record.getString("storageName"), record.getString("bin"),
        if (uri.scheme == "file") uri.path.orEmpty() else "")

    fun originalParent(item: MediaItem): String? = records().firstOrNull {
        it.optString("lastUri") == item.uriKey
    }?.getString("parent")

    suspend fun load(): List<MediaItem> = withContext(Dispatchers.IO) {
        lock.withLock {
            // Read on-drive sidecars too, so a bin remains recoverable after reinstalling the app.
            for (root in (mounted() + granted())) runCatching {
                val bin = StorageAccess.find(context, root.target, StorageAccess.BIN) ?: return@runCatching
                for ((name, path) in StorageAccess.children(context, bin)) if (name.endsWith(".fgallery.json")) {
                    val sidecar = if (path.startsWith("content://")) Uri.parse(path) else Uri.fromFile(File(path))
                    val record = context.contentResolver.openInputStream(sidecar)?.bufferedReader()?.use { JSONObject(it.readText()) } ?: continue
                    write(record.apply { put("bin", bin) })
                }
            }
            records().mapNotNull { record -> uri(record)?.let { found ->
                val local = if (found.scheme == "file") File(found.path!!) else null
                val document = if (local == null) androidx.documentfile.provider.DocumentFile.fromSingleUri(context, found) else null
                val actualName = local?.name ?: document?.name
                if (actualName?.startsWith(record.getString("token") + "_") == true) {
                    val name = actualName.removePrefix(record.getString("token") + "_")
                    record.put("name", name)
                    DriveLibrary.mime(name)?.let { record.put("mime", it) }
                }
                record.put("size", local?.length() ?: document?.length() ?: record.optLong("size"))
                record.put("modified", local?.lastModified() ?: document?.lastModified() ?: record.optLong("modified"))
                write(record.apply { put("lastUri", found.toString()) })
                item(record, found)
            } }
        }
    }
    suspend fun trash(items: List<MediaItem>): List<String> = withContext(Dispatchers.IO) {
        lock.withLock {
            items.map { item ->
                val source = StorageAccess.file(context, item)
                val root = mounted().firstOrNull { source != null && source.path.startsWith(it.directory!!.path + "/") }
                val grant = granted().filter { it.id == item.storageId }.firstOrNull { candidate ->
                    runCatching {
                        val base = android.provider.DocumentsContract.getDocumentId(Uri.parse(candidate.target)).trimEnd('/')
                        val sourceId = if (source != null && root != null) {
                            val prefix = if (root.id == "external_primary") "primary" else root.directory!!.name
                            "$prefix:${source.relativeTo(root.directory!!).invariantSeparatorsPath}"
                        } else android.provider.DocumentsContract.getDocumentId(item.uri)
                        sourceId == base || sourceId.startsWith("$base/") || (base.endsWith(':') && sourceId.startsWith(base))
                    }.getOrDefault(false)
                }
                val target = root?.directory?.takeIf { it.canWrite() }?.path ?: grant?.target ?: error("Нет доступа к накопителю ${item.storageName}")
                val folder = StorageAccess.directory(context, target, StorageAccess.BIN)
                val token = UUID.randomUUID().toString()
                val record = metadata(item, token, folder, StorageAccess.parent(context, item))
                write(record) // Journal before moving: an interruption cannot lose the original location.
                val sidecarName = "$token.fgallery.json"
                val sidecar = StorageAccess.create(context, folder, sidecarName, "application/json")
                try {
                    context.contentResolver.openOutputStream(sidecar, "w")!!.use { output ->
                        output.write(record.toString().toByteArray(Charsets.UTF_8)); output.flush()
                        if (output is java.io.FileOutputStream) output.fd.sync()
                    }
                    StorageAccess.move(context, item, folder, "${token}_${item.name}").toString().also { moved -> write(record.apply { put("lastUri", moved) }) }
                } catch (e: Exception) {
                    if (uri(record) == null) { runCatching { StorageAccess.delete(context, sidecar) }; forget(token) }
                    throw e
                }
            }
        }
    }
    suspend fun restore(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        lock.withLock {
            for (item in items) {
                val record = records().firstOrNull { uri(it) == item.uri }
                if (record == null) continue // Legacy virtual bin is handled by settings.
                val parent = StorageAccess.ensureDirectory(context, record.getString("parent"))
                StorageAccess.move(context, item.copy(name = "${record.getString("token")}_${item.name}"), parent, record.getString("name"))
                removeMetadata(record)
            }
        }
    }
    suspend fun deleted(uris: Collection<String>) = withContext(Dispatchers.IO) {
        lock.withLock { records().filter { uri(it)?.toString() in uris || it.optString("lastUri") in uris }.forEach(::removeMetadata) }
    }
    private fun removeMetadata(record: JSONObject) {
        runCatching {
            StorageAccess.find(context, record.getString("bin"), record.getString("token") + ".fgallery.json")?.let {
                StorageAccess.delete(context, if (it.startsWith("content://")) Uri.parse(it) else Uri.fromFile(File(it)))
            }
        }
        forget(record.getString("token"))
    }
    companion object { private val lock = Mutex() }
}

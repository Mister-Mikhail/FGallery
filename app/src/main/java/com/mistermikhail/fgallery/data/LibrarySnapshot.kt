package com.mistermikhail.fgallery.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The catalogue is shown immediately; a fresh scan replaces it in the background. */
internal object LibrarySnapshot {
    data class Value(val items: List<MediaItem>, val bin: Set<String>, val permission: Boolean)
    @Volatile var memory: Value? = null
        private set

    fun load(context: Context, permission: Boolean): Value? {
        memory?.takeIf { it.permission == permission }?.let { return it }
        return runCatching {
            val json = JSONObject(File(context.filesDir, "library_snapshot_v1.json").readText())
            if (json.getBoolean("permission") != permission) return@runCatching null
            val rows = json.getJSONArray("items")
            val items = (0 until rows.length()).map { i ->
                val r = rows.getJSONArray(i)
                MediaItem(r.getLong(0), Uri.parse(r.getString(1)), r.getString(2), r.optString(3).takeIf { it.isNotEmpty() },
                    MediaKind.valueOf(r.getString(4)), r.getLong(5), r.getInt(6), r.getInt(7), r.getLong(8), r.getString(9),
                    r.getString(10), r.getLong(11), r.getLong(12), r.getString(13), r.getString(14), r.getString(15), r.getString(16))
            }
            val bins = json.getJSONArray("bin")
            Value(items, (0 until bins.length()).mapTo(hashSetOf()) { bins.getString(it) }, permission).also { memory = it }
        }.getOrNull()
    }

    fun save(context: Context, value: Value) {
        memory = value
        val rows = JSONArray()
        value.items.forEach { i -> rows.put(JSONArray(listOf(i.id, i.uriKey, i.name, i.mimeType.orEmpty(), i.kind.name,
            i.dateTakenMillis, i.width, i.height, i.durationMillis, i.album, i.relativePath, i.sizeBytes, i.dateModifiedMillis,
            i.storageId, i.storageName, i.folderTarget, i.sourcePath))) }
        val temp = File(context.filesDir, "library_snapshot_v1.tmp")
        temp.writeText(JSONObject().put("permission", value.permission).put("items", rows).put("bin", JSONArray(value.bin.toList())).toString())
        check(temp.renameTo(File(context.filesDir, "library_snapshot_v1.json")))
    }
    internal fun forgetMemory() { memory = null }
    internal fun removeItems(uris: Set<String>) {
        memory = memory?.let { it.copy(items = it.items.filterNot { item -> item.uriKey in uris }) }
    }
}

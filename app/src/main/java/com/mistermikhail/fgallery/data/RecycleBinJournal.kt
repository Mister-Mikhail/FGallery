package com.mistermikhail.fgallery.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A durable record per move, with read support for the original preferences journal. */
internal class RecycleBinJournal(context: Context) {
    private val legacy = context.getSharedPreferences("fgallery_drive_bin_v1", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "fgallery-bin-journal")

    fun records(): Map<String, JSONObject> = buildMap {
        for ((token, value) in legacy.all) {
            if (value is String && validToken(token)) parse(token, value)?.let { put(token, it) }
        }
        for (entry in directory.listFiles().orEmpty()) {
            if (entry.extension != "json" || !validToken(entry.nameWithoutExtension)) continue
            val record = runCatching { parse(entry.nameWithoutExtension, entry.readText()) }.getOrNull()
            if (record != null) put(entry.nameWithoutExtension, record)
        }
    }

    fun write(record: JSONObject) {
        val token = record.getString("token")
        require(validToken(token))
        check(directory.isDirectory || directory.mkdirs()) { "Нет доступа к журналу корзины" }
        val temporary = File(directory, "$token.tmp")
        try {
            temporary.outputStream().use { output ->
                output.write(record.toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            check(temporary.renameTo(File(directory, "$token.json"))) { "Не удалось сохранить данные восстановления" }
        } finally {
            temporary.delete()
        }
    }

    fun forget(token: String) {
        require(validToken(token))
        File(directory, "$token.json").delete()
        if (legacy.contains(token)) legacy.edit().remove(token).commit()
    }

    private fun parse(token: String, value: String): JSONObject? =
        runCatching { JSONObject(value).takeIf { it.optString("token") == token } }.getOrNull()

    companion object {
        fun validToken(token: String): Boolean =
            token.length == 36 && runCatching { UUID.fromString(token).toString() == token }.getOrDefault(false)
    }
}

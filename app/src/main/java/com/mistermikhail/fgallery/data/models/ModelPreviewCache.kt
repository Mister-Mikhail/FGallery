package com.mistermikhail.fgallery.data.models

import android.app.ActivityManager
import android.content.Context
import com.mistermikhail.fgallery.data.MediaItem
import com.mistermikhail.fgallery.data.ModelFormat
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal object ModelPreviewCache {
    private const val VERSION = 1
    private const val DISK_QUOTA = 144L * 1024 * 1024
    private val preparation = Mutex()

    suspend fun load(context: Context, item: MediaItem, format: ModelFormat): ByteBuffer =
        withContext(Dispatchers.IO) {
            preparation.withLock {
                val job = currentCoroutineContext()
                val directory = File(context.cacheDir, "model-previews")
                check(directory.isDirectory || directory.mkdirs()) { "Кэш моделей недоступен" }
                directory.listFiles()?.filter { it.extension == "tmp" }?.forEach(File::delete)
                val profile = if (context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true) 1024 else 2048
                val file = if (item.uri.scheme == "file") item.uri.path?.let(::File) else null
                val size = file?.length() ?: item.sizeBytes
                val modified = file?.lastModified() ?: item.dateModifiedMillis
                require(size <= MAX_MODEL_FILE_BYTES) { "Допустимый размер модели — до 48 МиБ" }
                val key = "$VERSION|${format.extension}|$profile|${item.uriKey}|$size|$modified"
                val versioned = size > 0 && modified > 0
                val versionedEntry = File(directory, "${digest(key.toByteArray())}.glb")

                val source = context.contentResolver.openInputStream(item.uri)
                    ?: error("Нет доступа к файлу модели")
                source.use { input ->
                    // Open the source even on a cache hit to honor a revoked URI grant.
                    if (versioned && versionedEntry.isFile) return@withLock useEntry(versionedEntry)
                    val stagedSource = File.createTempFile("source-", ".tmp", directory)
                    try {
                        val digest = MessageDigest.getInstance("SHA-256").apply { update(key.toByteArray()) }
                        val fingerprinted = if (versioned) input else DigestInputStream(input, digest)
                        stage(fingerprinted, stagedSource, job::ensureActive)
                        val entry = if (versioned) versionedEntry else File(directory, "${digest.digest().hex()}.glb")
                        if (!entry.isFile) {
                            trim(directory, DISK_QUOTA - MAX_MODEL_FILE_BYTES)
                            val stagedModel = File.createTempFile("model-", ".tmp", directory)
                            try {
                                val model = stagedSource.mapModel()
                                when (format) {
                                    ModelFormat.GLB -> GlbPreview.prepare(model, stagedModel, profile, job::ensureActive)
                                    ModelFormat.STL -> GlbPreview.writeStl(stagedModel, StlMesh.parse(model, job::ensureActive))
                                }
                                job.ensureActive()
                                check(stagedModel.renameTo(entry)) { "Не удалось записать кэш модели" }
                            } finally {
                                stagedModel.delete()
                            }
                        }
                        useEntry(entry)
                    } finally {
                        stagedSource.delete()
                    }
                }
            }
        }

    private fun useEntry(entry: File): ByteBuffer {
        entry.setLastModified(System.currentTimeMillis())
        return entry.mapModel()
    }

    private fun stage(input: InputStream, file: File, checkActive: () -> Unit) {
        file.outputStream().buffered().use { output ->
            val bytes = ByteArray(64 * 1024)
            var size = 0
            while (true) {
                checkActive()
                val count = input.read(bytes)
                if (count < 0) break
                size += count
                require(size <= MAX_MODEL_FILE_BYTES) { "Допустимый размер модели — до 48 МиБ" }
                output.write(bytes, 0, count)
            }
        }
    }

    private fun trim(directory: File, targetBytes: Long) {
        val entries = directory.listFiles { file -> file.extension == "glb" }.orEmpty().sortedBy(File::lastModified)
        var total = entries.sumOf(File::length)
        for (entry in entries) {
            if (total <= targetBytes) break
            val size = entry.length()
            if (entry.delete()) total -= size
        }
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}

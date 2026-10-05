package com.mistermikhail.fgallery.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AvailableUpdate(val version: String, val url: String, val size: Long, val digest: String?)

class AppUpdates(private val context: Context) {
    private val preferences = context.getSharedPreferences("fgallery_updates", Context.MODE_PRIVATE)
    private val flags = PackageManager.GET_SIGNING_CERTIFICATES
    private fun installed() = context.packageManager.getPackageInfo(context.packageName, flags)
    val installedVersion: String get() = installed().versionName.orEmpty()

    suspend fun check(manual: Boolean): AvailableUpdate? = withContext(Dispatchers.IO) {
        if (!manual && System.currentTimeMillis() - preferences.getLong("checked", 0) < 6 * 60 * 60 * 1000L) return@withContext null
        preferences.edit().putLong("checked", System.currentTimeMillis()).apply()
        val connection = connection("https://api.github.com/repos/Mister-Mikhail/FGallery/releases/latest")
        try {
            if (connection.responseCode == 404) return@withContext null
            check(connection.responseCode == 200) { "Не удалось проверить обновления (${connection.responseCode})" }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(text)
            val version = release.getString("tag_name").removePrefix("v")
            if (!isNewerVersion(version, installedVersion)) return@withContext null
            if (!manual && preferences.getString("declined", "") == version) return@withContext null
            val assets = release.getJSONArray("assets")
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.getString("name").endsWith(".apk") }
            val asset = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> apks.firstOrNull { abi in it.getString("name") } }
                ?: apks.firstOrNull { "universal" in it.getString("name") } ?: apks.singleOrNull()
                ?: error("APK для этого устройства пока не опубликован")
            val url = asset.getString("browser_download_url")
            require(url.startsWith("https://github.com/Mister-Mikhail/FGallery/releases/download/")) { "Некорректный адрес обновления" }
            AvailableUpdate(version, url, asset.optLong("size"), asset.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"))
        } finally { connection.disconnect() }
    }

    fun decline(update: AvailableUpdate) { preferences.edit().putString("declined", update.version).apply() }

    suspend fun download(update: AvailableUpdate, progress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(directory, "update.part")
        val target = File(directory, "FGallery-${update.version}.apk")
        val connection = connection(update.url)
        try {
            check(connection.responseCode == 200) { "Не удалось скачать обновление (${connection.responseCode})" }
            val hash = MessageDigest.getInstance("SHA-256")
            var count = 0L
            connection.inputStream.use { input -> partial.outputStream().use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read); hash.update(buffer, 0, read); count += read
                    progress(count, update.size)
                }
                output.fd.sync()
            } }
            check(count > 0 && (update.size <= 0 || count == update.size)) { "Обновление скачано не полностью" }
            val actual = hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            check(update.digest == null || actual.equals(update.digest, true)) { "Контрольная сумма обновления не совпала" }
            val archive = context.packageManager.getPackageArchiveInfo(partial.path, flags) ?: error("Некорректный APK")
            check(archive.packageName == context.packageName) { "APK относится к другому приложению" }
            check(archive.longVersionCode > installed().longVersionCode) { "Эта версия уже установлена" }
            fun signers(info: android.content.pm.PackageInfo) = info.signingInfo?.apkContentsSigners?.map { signer ->
                MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
            }?.toSet().orEmpty()
            check(signers(archive).isNotEmpty() && signers(archive) == signers(installed())) {
                "Подпись APK отличается от установленного приложения. Требуется версия с тем же ключом подписи."
            }
            currentCoroutineContext().ensureActive()
            target.delete()
            check(partial.renameTo(target)) { "Не удалось сохранить обновление" }
            target
        } finally { connection.disconnect(); partial.delete() }
    }

    private fun connection(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000; readTimeout = 15000
        setRequestProperty("User-Agent", "FGallery/$installedVersion")
        setRequestProperty("Accept", "application/vnd.github+json")
    }

    companion object {
        internal fun isNewerVersion(candidate: String, installed: String): Boolean {
            fun parts(value: String): List<Long>? {
                val text = value.removePrefix("v").takeIf { it.matches(Regex("\\d+\\.\\d+\\.\\d+")) } ?: return null
                return text.split('.').map { it.toLongOrNull() ?: return null }
            }
            val next = parts(candidate) ?: return false
            val current = parts(installed) ?: return false
            return next.zip(current).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
        }
    }
}

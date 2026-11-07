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

data class AvailableUpdate(val version: String, val versionCode: Long, val tag: String, val url: String, val size: Long, val digest: String?)

class AppUpdates(
    private val context: Context,
    private val openConnection: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
    private val packageInfo: () -> android.content.pm.PackageInfo = {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    },
) {
    private val preferences = context.getSharedPreferences("fgallery_updates", Context.MODE_PRIVATE)
    private val flags = PackageManager.GET_SIGNING_CERTIFICATES
    private fun installed() = packageInfo()
    val installedVersion: String get() = installed().versionName.orEmpty()

    suspend fun check(manual: Boolean): AvailableUpdate? = withContext(Dispatchers.IO) {
        // GitHub's /releases/latest ignores prereleases. Compare Android
        // versionCodes so builds with identical versionName are discoverable.
        if (!manual && System.currentTimeMillis() - preferences.getLong("checked", 0) < 6 * 60 * 60 * 1000L) return@withContext null
        val connection = connection("https://api.github.com/repos/Mister-Mikhail/FGallery/releases?per_page=50")
        try {
            check(connection.responseCode == 200) { "Не удалось проверить обновления (${connection.responseCode})" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val releases = org.json.JSONArray(body)
            val installed = installed()
            val available = (0 until releases.length()).mapNotNull { index ->
                val release = releases.optJSONObject(index) ?: return@mapNotNull null
                if (release.optBoolean("draft", true)) return@mapNotNull null
                val tag = release.optString("tag_name")
                val notes = release.optString("body")
                val (version, build) = releaseVersionAndCode(
                    tag, notes, installed.versionName.orEmpty(), installed.longVersionCode,
                ) ?: return@mapNotNull null
                if (build <= installed.longVersionCode) return@mapNotNull null
                if (!manual && preferences.getString("declined", "") == tag) return@mapNotNull null
                val assets = release.optJSONArray("assets") ?: return@mapNotNull null
                val apks = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                    .filter { it.optString("name").endsWith(".apk") }
                val asset = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
                    apks.firstOrNull { "-$abi.apk" in it.optString("name") }
                } ?: apks.firstOrNull { "-universal.apk" in it.optString("name") }
                    ?: return@mapNotNull null
                val url = asset.optString("browser_download_url")
                if (!url.startsWith("https://github.com/Mister-Mikhail/FGallery/releases/download/")) return@mapNotNull null
                val assetName = asset.optString("name")
                val checksumKey = if ("-universal.apk" in assetName) "UNIVERSAL" else "ARM64"
                val fromNotes = Regex("(?m)^FGALLERY_SHA256_$checksumKey=([a-fA-F0-9]{64})\\s*$")
                    .find(notes)?.groupValues?.get(1)
                val digest = asset.optString("digest").takeIf { it.startsWith("sha256:") }
                    ?.removePrefix("sha256:") ?: fromNotes
                if (tag.startsWith("qa-") && digest == null) return@mapNotNull null
                AvailableUpdate(version, build, tag, url, asset.optLong("size"), digest)
            }.maxWithOrNull(compareBy<AvailableUpdate> { it.versionCode }
                .thenBy { !it.tag.startsWith("qa-") })
            currentCoroutineContext().ensureActive()
            preferences.edit().putLong("checked", System.currentTimeMillis()).apply()
            available
        } finally { connection.disconnect() }
    }

    fun decline(update: AvailableUpdate) { preferences.edit().putString("declined", update.tag).apply() }

    suspend fun download(update: AvailableUpdate, progress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(directory, "update.part")
        val target = File(directory, "FGallery-${update.version}-${update.versionCode}.apk")
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

    private fun connection(url: String) = openConnection(url).apply {
        connectTimeout = 15000; readTimeout = 15000
        setRequestProperty("User-Agent", "FGallery/$installedVersion")
        setRequestProperty("Accept", "application/vnd.github+json")
    }

    companion object {
        // Immutable QA tags carry versionCode despite reused versionName.
        internal fun releaseVersionAndCode(
            tag: String, notes: String, currentVersion: String, currentCode: Long,
        ): Pair<String, Long>? {
            val qa = Regex("""qa-v(\d+\.\d+\.\d+)-b(\d+)""").matchEntire(tag)
            if (qa != null) {
                val code = qa.groupValues[2].toLongOrNull() ?: return null
                return qa.groupValues[1] to code
            }
            val stable = Regex("""v(\d+\.\d+\.\d+)""").matchEntire(tag) ?: return null
            val version = stable.groupValues[1]
            val code = Regex("""(?m)^FGALLERY_VERSION_CODE=(\d+)\s*$""")
                .find(notes)?.groupValues?.get(1)?.toLongOrNull()
                // Preserve old stable releases without build metadata;
                // Android checks APK versionCode again before install.
                ?: if (isNewerVersion(version, currentVersion)) currentCode + 1 else return null
            return version to code
        }

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

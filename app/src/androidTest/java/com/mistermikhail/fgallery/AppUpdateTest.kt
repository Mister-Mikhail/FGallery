package com.mistermikhail.fgallery

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AppUpdateTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun resetPreferences() { context.getSharedPreferences("fgallery_updates", 0).edit().clear().commit() }
    private fun response(code: Int = 200, body: () -> InputStream) = object : HttpURLConnection(URL("https://github.com/")) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream() = body()
    }
    private fun previousInstalledVersion(): android.content.pm.PackageInfo {
        val source = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val parcel = android.os.Parcel.obtain()
        try {
            source.writeToParcel(parcel, 0); parcel.setDataPosition(0)
            return android.content.pm.PackageInfo.CREATOR.createFromParcel(parcel).apply { setLongVersionCode(longVersionCode - 1) }
        } finally { parcel.recycle() }
    }
    @Test fun manualCheckSelectsAnApkAndCanReofferAnAutomaticallyDeclinedVersion() = runBlocking {
        var requests = 0
        val nextVersion = AppUpdates(context).installedVersion.split('.').let { "${it[0]}.${it[1]}.${it[2].toInt() + 1}" }
        val json = """{"tag_name":"v$nextVersion","assets":[{"name":"FGallery-$nextVersion-universal.apk","browser_download_url":"https://github.com/Mister-Mikhail/FGallery/releases/download/v$nextVersion/FGallery.apk","size":100,"digest":"sha256:abcd"}]}"""
        val updates = AppUpdates(context, { requests++; response { ByteArrayInputStream(json.toByteArray()) } })
        val candidate = updates.check(false)!!
        assertEquals(nextVersion, candidate.version); assertEquals("abcd", candidate.digest)
        assertNull(updates.check(false)); assertEquals(1, requests)
        updates.decline(candidate)
        context.getSharedPreferences("fgallery_updates", 0).edit().putLong("checked", 0).commit()
        assertNull(updates.check(false))
        assertNotNull(updates.check(true))
    }
    @Test fun failedAutomaticChecksDoNotSuppressRetryForSixHours() = runBlocking {
        var requests = 0
        val updates = AppUpdates(context, { requests++; response(503) { ByteArrayInputStream(byteArrayOf()) } })
        repeat(2) { try { updates.check(false); fail("HTTP error expected") } catch (_: IllegalStateException) {} }
        assertEquals(2, requests)
    }
    @Test fun downloadedSignedApkIsValidatedAndIncompleteOrCancelledFilesAreDiscarded() = runBlocking {
        val apk = File(context.applicationInfo.sourceDir)
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input -> val buffer = ByteArray(128 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        val installed = previousInstalledVersion()
        // Model the same signed package installed at the previous version.
        val updates = AppUpdates(context, { response { apk.inputStream() } }, { installed })
        val candidate = AvailableUpdate("0.2.6", "https://github.com/Mister-Mikhail/FGallery/releases/download/v0.2.6/FGallery.apk", apk.length(), hash)
        val output = updates.download(candidate) { _, _ -> }
        assertEquals(apk.length(), output.length()); output.delete()
        try { updates.download(candidate.copy(size = apk.length() + 1)) { _, _ -> }; fail("Truncated download must fail") }
        catch (_: IllegalStateException) {}
        assertFalse(File(context.cacheDir, "updates/update.part").exists())
        try { updates.download(candidate) { _, _ -> throw CancellationException("User cancelled") }; fail("Cancellation must propagate") }
        catch (_: CancellationException) {}
        assertFalse(File(context.cacheDir, "updates/update.part").exists())
        assertFalse(output.exists())
        assertTrue("Installed APK remains intact", apk.isFile)
    }
    @Test fun differentSigningCertificateCannotBecomeAnUpdate() = runBlocking {
        val apk = File(context.applicationInfo.sourceDir)
        val installed = previousInstalledVersion()
        installed.signingInfo = null
        val updates = AppUpdates(context, { response { apk.inputStream() } }, { installed })
        try {
            updates.download(AvailableUpdate("0.2.6", "https://github.com/", apk.length(), null)) { _, _ -> }
            fail("Unrelated/missing signer must fail")
        } catch (error: IllegalStateException) { assertTrue(error.message.orEmpty().contains("Подпись")) }
        assertFalse(File(context.cacheDir, "updates/update.part").exists())
    }
}

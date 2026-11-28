package com.mistermikhail.fgallery

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.*
import com.mistermikhail.fgallery.ui.UpdateOfferMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest

class AppUpdateTest {
    @get:Rule val compose = createComposeRule()
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
        val nextVersion = AppUpdates(context).installedVersion.substringBefore('-').split('.').let { "${it[0]}.${it[1]}.${it[2].toInt() + 1}" }
        val json = """[{"tag_name":"v$nextVersion","draft":false,"assets":[{"name":"FGallery-$nextVersion-universal.apk","browser_download_url":"https://github.com/Mister-Mikhail/FGallery/releases/download/v$nextVersion/FGallery.apk","size":100,"digest":"sha256:abcd"}]}]"""
        val updates = AppUpdates(context, { requests++; response { ByteArrayInputStream(json.toByteArray()) } })
        val candidate = updates.check(false)!!
        assertEquals(nextVersion, candidate.version); assertEquals("abcd", candidate.digest)
        assertNull(updates.check(false)); assertEquals(1, requests)
        updates.decline(candidate)
        context.getSharedPreferences("fgallery_updates", 0).edit().putLong("checked", 0).commit()
        assertNull(updates.check(false))
        assertNotNull(updates.check(true))
    }
    @Test fun sameVersionNameHigherQaVersionCodeIsOfferedWithChanges() {
        val installed = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val name = installed.versionName.orEmpty().substringBefore('-')
        val build = installed.longVersionCode + 1
        val tag = "qa-v$name-b$build"
        val json = """[{"tag_name":"$tag","draft":false,"prerelease":true,
            "body":"### Что нового\n- Исправлена прокрутка групп.\nFGALLERY_VERSION_CODE=$build",
            "assets":[{"name":"FGallery-$name-b$build-universal.apk",
            "browser_download_url":"https://github.com/Mister-Mikhail/FGallery/releases/download/$tag/FGallery-$name-b$build-universal.apk",
            "size":100,"digest":"sha256:abcd"}]}]"""
        val updates = AppUpdates(context, { response { ByteArrayInputStream(json.toByteArray()) } })
        val candidate = runBlocking { updates.check(true) }!!
        assertEquals(name, candidate.version)
        assertEquals(build, candidate.versionCode)
        assertEquals(tag, candidate.tag)
        compose.setContent { UpdateOfferMessage(candidate) }
        compose.onNodeWithText("Что нового").assertIsDisplayed()
        compose.onNodeWithText("• Исправлена прокрутка групп.").assertIsDisplayed()
        compose.onNodeWithText("FGALLERY_VERSION_CODE=$build").assertDoesNotExist()
    }

    @Test fun failedAutomaticChecksDoNotSuppressRetryForSixHours() = runBlocking {
        var requests = 0
        val updates = AppUpdates(context, { requests++; response(503) { ByteArrayInputStream(byteArrayOf()) } })
        repeat(2) {
            try {
                updates.check(false)
                fail("HTTP error expected")
            } catch (failure: IllegalStateException) {
                assertTrue(failure.message.orEmpty().contains("Проверьте подключение"))
            }
        }
        assertEquals(4, requests)
    }

    @Test fun atomFallbackReportsNoUpdateAndIncludesNotesWhenOneAppears() {
        val installed = previousInstalledVersion()
        val version = installed.versionName.orEmpty().substringBefore('-')
        val digest = "a".repeat(64)
        var offered = installed.longVersionCode
        var assetRequests = 0
        val updates = AppUpdates(context, { url ->
            val tag = "qa-v$version-b$offered"
            when (url) {
                "$FGALLERY_RELEASES.atom" -> response {
                    """<feed xmlns="http://www.w3.org/2005/Atom"><entry>
                      <link rel="alternate" href="$FGALLERY_RELEASES/tag/$tag"/>
                      <content type="html">&lt;h3&gt;Что нового&lt;/h3&gt;
                      &lt;ul&gt;&lt;li&gt;Место съёмки на карте.&lt;/li&gt;&lt;/ul&gt;
                      &lt;p&gt;FGALLERY_VERSION_CODE=$offered&lt;br&gt;FGALLERY_SHA256_UNIVERSAL=$digest&lt;/p&gt;
                      </content></entry></feed>""".trimIndent().byteInputStream()
                }
                "$FGALLERY_RELEASES/expanded_assets/$tag" -> response {
                    assetRequests++
                    """<a href="/Mister-Mikhail/FGallery/releases/download/$tag/FGallery-universal.apk">APK</a>""".byteInputStream()
                }
                else -> throw UnknownHostException("api.github.com")
            }
        }, { installed })
        assertNull(runBlocking { updates.check(true) })
        assertEquals(0, assetRequests)
        offered++
        val candidate = checkNotNull(runBlocking { updates.check(true) })
        assertEquals(offered, candidate.versionCode)
        assertEquals(digest, candidate.digest)
        compose.setContent { UpdateOfferMessage(candidate) }
        compose.onNodeWithText("• Место съёмки на карте.").assertIsDisplayed()
    }
    @Test fun downloadedSignedApkIsValidatedAndIncompleteOrCancelledFilesAreDiscarded() = runBlocking {
        val apk = File(context.applicationInfo.sourceDir)
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input -> val buffer = ByteArray(128 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        val installed = previousInstalledVersion()
        // Model the same signed package installed at the previous version.
        val updates = AppUpdates(context, { response { apk.inputStream() } }, { installed })
        val currentVersion = AppUpdates(context).installedVersion
        val candidate = AvailableUpdate(currentVersion, installed.longVersionCode + 1, "qa-v$currentVersion-b${installed.longVersionCode + 1}", "https://github.com/Mister-Mikhail/FGallery/releases/download/v$currentVersion/FGallery.apk", apk.length(), hash)
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
            updates.download(AvailableUpdate(AppUpdates(context).installedVersion, installed.longVersionCode + 1, "qa-test", "https://github.com/", apk.length(), null)) { _, _ -> }
            fail("Unrelated/missing signer must fail")
        } catch (error: IllegalStateException) { assertTrue(error.message.orEmpty().contains("Подпись")) }
        assertFalse(File(context.cacheDir, "updates/update.part").exists())
    }
}

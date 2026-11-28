package com.mistermikhail.fgallery

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.DocumentLibrary
import com.mistermikhail.fgallery.data.MediaKind
import com.mistermikhail.fgallery.data.ModelFormat
import com.mistermikhail.fgallery.data.StorageAccess
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class ModelSafLaunchTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun genericModelFilesAppearInTheChooserWithoutClaimingUnrelatedFiles() {
        fun resolve(uri: String, type: String?) = Intent(Intent.ACTION_VIEW).setPackage(context.packageName)
            .setDataAndType(Uri.parse(uri), type)
            .resolveActivity(context.packageManager)
        val documents = "content://com.android.externalstorage.documents/document/"
        assertNotNull(resolve(documents + "primary%3ADownload%2Fpart.v2.stl", "application/octet-stream"))
        assertNotNull(resolve(documents + "primary%3ADownload%2Fscene.GLB", "application/octet-stream"))
        assertNotNull(resolve(documents + "primary%3ADownload%2Fpart.stl", "application/vnd.ms-pki.stl"))
        assertNotNull(resolve(documents + "primary%3ADownload%2Fscene.glb", null))
        assertNotNull(resolve("content://model.provider/item/42", "application/x-glb"))
        assertNull(resolve(documents + "primary%3ADownload%2Farchive.zip", "application/octet-stream"))
        assertNull(resolve(documents + "primary%3ADownload%2Fpart.stl.exe", "application/octet-stream"))
    }

    @Test fun safImportAndExternalViewOpenModelOutsideCatalog() {
        val file = File(context.cacheDir, "external-model.glb")
        writeTexturedModelFixture(file)
        val provider = ComponentName(context, TestDriveProvider::class.java)
        val manager = context.packageManager
        val initialState = manager.getComponentEnabledSetting(provider)
        manager.setComponentEnabledSetting(provider, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        val prefs = context.getSharedPreferences("fgallery_documents", 0)
        val imported = prefs.getStringSet("uris", null)?.toSet()
        val tree = DocumentsContract.buildTreeDocumentUri("com.mistermikhail.fgallery.test.drives", "root")
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, "root")
        val name = "test-view-${System.nanoTime()}.glb"
        val source = StorageAccess.create(context, parent.toString(), name, ModelFormat.GLB.mimeType)
        val onboarding = context.getSharedPreferences("fgallery_onboarding", 0)
        val onboardingCompleted = onboarding.getBoolean("access_wizard_completed", false)
        try {
            requireNotNull(context.contentResolver.openOutputStream(source)).use {
                file.inputStream().use { input -> input.copyTo(it) }
            }
            val library = DocumentLibrary(context)
            library.add(listOf(source))
            val saved = runBlocking { library.load().single { it.uri == source } }
            assertEquals(MediaKind.MODEL, saved.kind)
            // Open the external URI without relying on the imported catalogue record.
            prefs.edit().putStringSet("uris", imported).commit()
            onboarding.edit().putBoolean("access_wizard_completed", true).commit()
            val external = Intent(Intent.ACTION_VIEW).setPackage(context.packageName)
                .setDataAndType(source, ModelFormat.GLB.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ActivityScenario.launch<MainActivity>(external).use {
                rule.waitUntil(22_000) { rule.onAllNodesWithTag("model-ready").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText(name).assertIsDisplayed()
            }
        } finally {
            prefs.edit().putStringSet("uris", imported).commit()
            onboarding.edit().putBoolean("access_wizard_completed", onboardingCompleted).commit()
            StorageAccess.delete(context, source)
            manager.setComponentEnabledSetting(provider, initialState, PackageManager.DONT_KILL_APP)
            file.delete()
        }
    }
}

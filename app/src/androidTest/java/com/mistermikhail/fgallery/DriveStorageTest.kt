package com.mistermikhail.fgallery

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class DriveStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun eachDriveKeepsItsOwnBinAndReconnectPreservesRestoreLocation() = runBlocking {
        val base = File(context.cacheDir, "drives-${UUID.randomUUID()}").apply { mkdirs() }
        val primary = File(base, "internal").apply { mkdirs() }
        val removable = File(base, "usb").apply { mkdirs() }
        val roots = listOf(StorageRoot("external_primary", "Internal", primary.path, primary), StorageRoot("usb-test", "USB", removable.path, removable))
        var connected = roots
        val bin = DriveRecycleBin(context, { connected }, { emptyList() })
        fun source(root: StorageRoot, name: String): MediaItem {
            val file = File(root.directory, "photos/$name").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(100000) { (it % 251).toByte() }) }
            return DriveLibrary.item(Uri.fromFile(file), name, "image/jpeg", file.length(), file.lastModified(), root, file.parent!!)!!
        }
        val a = source(roots[0], "a.jpg"); val b = source(roots[1], "b.jpg")
        try {
            bin.trash(listOf(a, b))
            assertFalse(File(a.sourcePath).exists()); assertFalse(File(b.sourcePath).exists())
            val loaded = bin.load().filter { it.storageId in roots.map { root -> root.id } && it.folderTarget.startsWith(base.path) }
            assertEquals(2, loaded.size)
            assertTrue(loaded.any { it.uri.path!!.startsWith(primary.path + "/${StorageAccess.BIN}/") })
            assertTrue(loaded.any { it.uri.path!!.startsWith(removable.path + "/${StorageAccess.BIN}/") })
            // Simulate an actual detached directory, then recreate the store as after app restart.
            val unplugged = File(base, "detached"); assertTrue(removable.renameTo(unplugged)); connected = roots.take(1)
            assertEquals(1, bin.load().count { it.folderTarget.startsWith(base.path) })
            assertTrue(unplugged.renameTo(removable)); connected = roots
            val restarted = DriveRecycleBin(context, { connected }, { emptyList() })
            val reconnected = restarted.load().filter { it.folderTarget.startsWith(base.path) }
            assertEquals(2, reconnected.size)
            File(a.sourcePath).writeText("existing")
            try { restarted.restore(listOf(reconnected.first { it.name == "a.jpg" })); fail("Must not overwrite an existing original") } catch (_: IllegalStateException) { }
            assertEquals("existing", File(a.sourcePath).readText())
            File(a.sourcePath).delete()
            restarted.restore(reconnected)
            assertEquals(100000, File(a.sourcePath).length().toInt()); assertEquals(100000, File(b.sourcePath).length().toInt())
            assertTrue(restarted.load().none { it.folderTarget.startsWith(base.path) })
        } finally { base.deleteRecursively() }
    }
    @Test fun movesAcrossDrivesVerifyBytesAndPreserveSourceOnCollision() = runBlocking {
        val base = File(context.cacheDir, "move-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(base, "source.jpg").apply { writeBytes(ByteArray(150000) { (it % 239).toByte() }) }
        val destination = File(base, "destination").apply { mkdirs() }
        val root = StorageRoot("test", "Test", base.path, base)
        val item = DriveLibrary.item(Uri.fromFile(source), source.name, "image/jpeg", source.length(), 0, root, base.path)!!
        try {
            val expected = source.readBytes()
            File(destination, source.name).writeText("collision")
            try { StorageAccess.move(context, item, destination.path); fail("Collision must fail") } catch (_: IllegalStateException) { }
            assertArrayEquals(expected, source.readBytes())
            File(destination, source.name).delete()
            val moved = StorageAccess.move(context, item, destination.path)
            assertFalse(source.exists()); assertArrayEquals(expected, File(moved.path!!).readBytes())
            assertTrue(File(StorageAccess.directory(context, destination.path, "empty")).isDirectory)
        } finally { base.deleteRecursively() }
    }
    @Test fun documentOnlyDriveSupportsMoveBinAndRestore() = runBlocking {
        val component = android.content.ComponentName(context, TestDriveProvider::class.java)
        context.packageManager.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
        try {
        val authority = "com.mistermikhail.fgallery.test.drives"
        val tree = android.provider.DocumentsContract.buildTreeDocumentUri(authority, "root")
        val rootUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, "root")
        val root = StorageRoot("saf-test", "SAF drive", rootUri.toString())
        val folder = StorageAccess.directory(context, root.target, "photos-${UUID.randomUUID()}")
        val source = StorageAccess.create(context, folder, "saf.jpg", "image/jpeg")
        val expected = ByteArray(100000) { (it % 251).toByte() }
        context.contentResolver.openOutputStream(source, "w")!!.use { it.write(expected) }
        val item = DriveLibrary.item(source, "saf.jpg", "image/jpeg", expected.size.toLong(), 0, root, folder)!!
        val bin = DriveRecycleBin(context, { emptyList() }, { listOf(root) })
        bin.trash(listOf(item))
        assertNull(StorageAccess.find(context, folder, "saf.jpg"))
        val recycled = bin.load().first { it.name == "saf.jpg" && it.storageId == "saf-test" }
        assertArrayEquals(expected, context.contentResolver.openInputStream(recycled.uri)!!.use { it.readBytes() })
        bin.restore(listOf(recycled))
        val restored = Uri.parse(StorageAccess.find(context, folder, "saf.jpg")!!)
        assertArrayEquals(expected, context.contentResolver.openInputStream(restored)!!.use { it.readBytes() })
        StorageAccess.delete(context, restored)
        StorageAccess.delete(context, Uri.parse(folder))
        Unit
        } finally {
            context.packageManager.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
        }
    }

}

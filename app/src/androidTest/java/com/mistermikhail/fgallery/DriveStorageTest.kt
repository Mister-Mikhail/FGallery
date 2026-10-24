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
            val edited = File(base, "edited.jpg").apply { writeBytes(byteArrayOf(10, 20, 30, 40)) }
            val cropCopy = EditedMediaStore(context).saveCopy(loaded.first { it.name == "a.jpg" }, Uri.fromFile(edited), "image/jpeg", "jpg")
            val copiedFile = StorageAccess.file(context, loaded.first { it.name == "a.jpg" }.copy(uri = cropCopy, sourcePath = ""))!!
            assertEquals(File(a.sourcePath).parentFile!!.path, copiedFile.parentFile!!.path)
            assertArrayEquals(edited.readBytes(), copiedFile.readBytes())
            assertFalse(File(a.sourcePath).exists())
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
    @Test fun massMoveOf2501FilesKeepsContentsAndDoesNotOverwriteCollision() = runBlocking {
        val base = File(context.cacheDir, "mass-move-${UUID.randomUUID()}").apply { mkdirs() }
        val sourceFolder = File(base, "from").apply { mkdirs() }
        val destinationFolder = File(base, "to").apply { mkdirs() }
        val root = StorageRoot("mass-test", "Test removable drive", base.path, base)
        try {
            val sources = (1..2501).map { index ->
                val f = File(sourceFolder, "file-${index}.jpg")
                f.writeBytes(byteArrayOf((index % 251).toByte(), (index / 251).toByte()))
                DriveLibrary.item(Uri.fromFile(f), f.name, "image/jpeg", f.length(), 0, root, sourceFolder.path)!!
            }
            for (item in sources) {
                StorageAccess.move(context, item, destinationFolder.path)
            }
            assertTrue(sourceFolder.listFiles().isNullOrEmpty())
            assertEquals(2501, destinationFolder.listFiles()!!.size)
            for (index in 1..2501) {
                assertArrayEquals(
                    byteArrayOf((index % 251).toByte(), (index / 251).toByte()),
                    File(destinationFolder, "file-${index}.jpg").readBytes(),
                )
            }
            val collision = File(sourceFolder, "file-1.jpg").apply { writeText("original") }
            try {
                val item = DriveLibrary.item(Uri.fromFile(collision), collision.name, "image/jpeg",
                    collision.length(), 0, root, sourceFolder.path)!!
                StorageAccess.move(context, item, destinationFolder.path)
                fail("A duplicate destination must never overwrite either file")
            } catch (_: IllegalStateException) {
                assertEquals("original", collision.readText())
                assertEquals(2L, File(destinationFolder, "file-1.jpg").length())
            }
        } finally {
            base.deleteRecursively()
        }
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

    @Test fun editedBinFileKeepsItsRestoreEntryWhenTheFormatChanges() = runBlocking {
        val base = File(context.cacheDir, "bin-edit-${UUID.randomUUID()}").apply { mkdirs() }
        val root = StorageRoot("edit-test", "Test drive", base.path, base)
        val file = File(base, "source.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val source = DriveLibrary.item(Uri.fromFile(file), file.name, "image/jpeg", file.length(), 0, root, base.path)!!
        val bin = DriveRecycleBin(context, { listOf(root) }, { emptyList() })
        try {
            bin.trash(listOf(source))
            val before = bin.load().first { it.folderTarget.startsWith(base.path) }
            val edited = File(base, "edited.png").apply { writeBytes(byteArrayOf(5, 6, 7, 8, 9)) }
            EditedMediaStore(context).replace(before, Uri.fromFile(edited), "image/png", "png")
            // Recover from the unchanged on-drive sidecar, including after app restart.
            val restarted = DriveRecycleBin(context, { listOf(root) }, { emptyList() })
            val after = restarted.load().first { it.folderTarget.startsWith(base.path) }
            assertEquals("source.png", after.name)
            assertEquals("image/png", after.mimeType)
            assertEquals(5L, after.sizeBytes)
            restarted.restore(listOf(after))
            assertArrayEquals(edited.readBytes(), File(base, "source.png").readBytes())
        } finally { base.deleteRecursively() }
    }

}

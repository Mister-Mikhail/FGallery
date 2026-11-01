package com.mistermikhail.fgallery

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.CatalogDatabase
import com.mistermikhail.fgallery.data.CatalogFolderRow
import com.mistermikhail.fgallery.data.CatalogMediaRow
import com.mistermikhail.fgallery.data.CatalogVolumeRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogDatabaseTest {
    @Test fun transactionalMediaAndVolumeSnapshotSupportsDeletionWithoutTouchingFiles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        try {
            val file = CatalogMediaRow(
                uri = "content://media/external_primary/images/media/123",
                mediaId = 123,
                name = "test.jpg",
                mimeType = "image/jpeg",
                kind = "IMAGE",
                dateTakenMillis = 123L,
                width = 100,
                height = 60,
                durationMillis = 0,
                album = "QA",
                relativePath = "DCIM/QA/",
                sizeBytes = 999,
                dateModifiedMillis = 444,
                storageId = "external_primary",
                storageName = "Внутренняя память",
                folderTarget = "/storage/emulated/0/DCIM/QA",
                sourcePath = "/storage/emulated/0/DCIM/QA/test.jpg",
            )
            val folder = CatalogFolderRow("external_primary/DCIM/QA", "external_primary", "DCIM/QA/", 222, 333)
            val volume = CatalogVolumeRow("external_primary", "Внутренняя память", 42, "v1", 333, true)
            db.catalog().replaceSnapshot(listOf(file), listOf(folder), listOf(volume))
            assertEquals(file, db.catalog().allMedia().single())
            assertEquals(42L, db.catalog().allVolumes().single().mediaStoreGeneration)
            db.catalog().deleteMedia(listOf(file.uri))
            assertTrue(db.catalog().allMedia().isEmpty())
            assertEquals(folder, db.catalog().allFolders().single())
        } finally { db.close() }
    }

    @Test fun disconnectedVolumeKeepsRowsButReconnectReconcilesDeletions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        try {
            val internal = CatalogMediaRow(
                uri = "content://media/external_primary/images/media/1",
                mediaId = 1, name = "phone.jpg", mimeType = "image/jpeg",
                kind = "IMAGE", dateTakenMillis = 1L, width = 10, height = 10,
                durationMillis = 0L, album = "DCIM", relativePath = "DCIM/",
                sizeBytes = 10L, dateModifiedMillis = 1L, storageId = "external_primary",
                storageName = "Phone", folderTarget = "/phone/DCIM",
                sourcePath = "/phone/DCIM/phone.jpg",
            )
            val usb = internal.copy(
                uri = "content://media/usb/images/media/2", mediaId = 2,
                name = "usb.jpg", storageId = "usb", storageName = "USB",
                folderTarget = "/usb/DCIM", sourcePath = "/usb/DCIM/usb.jpg",
            )
            val phoneFolder = CatalogFolderRow("external_primary//phone/DCIM", "external_primary", "/phone/DCIM", 10L, 20L)
            val usbFolder = CatalogFolderRow("usb//usb/DCIM", "usb", "/usb/DCIM", 10L, 20L)
            val phoneVolume = CatalogVolumeRow("external_primary", "Phone", 5L, "v1", 20L, true)
            val usbVolume = CatalogVolumeRow("usb", "USB", 8L, "v1", 20L, true)
            val dao = db.catalog()
            dao.replaceSnapshot(listOf(internal, usb), listOf(phoneFolder, usbFolder), listOf(phoneVolume, usbVolume))

            // No USB files are scanned while disconnected: preserve the
            // original media and folder rows, without rewriting their bytes.
            dao.synchronizeSnapshot(
                listOf(internal), listOf(phoneFolder),
                listOf(phoneVolume, usbVolume.copy(connected = false)),
            )
            assertEquals(setOf(internal.uri, usb.uri), dao.allMedia().map { it.uri }.toSet())
            assertEquals(setOf(phoneFolder.key, usbFolder.key), dao.allFolders().map { it.key }.toSet())
            assertFalse(dao.allVolumes().single { it.storageId == "usb" }.connected)

            // On reconnection, the now-connected USB is authoritative. A
            // deleted file is removed from Room only, never from the drive.
            dao.synchronizeSnapshot(
                listOf(internal), listOf(phoneFolder),
                listOf(phoneVolume, usbVolume.copy(connected = true)),
            )
            assertEquals(listOf(internal.uri), dao.allMedia().map { it.uri })
            assertEquals(listOf(phoneFolder.key), dao.allFolders().map { it.key })
            assertTrue(dao.allVolumes().single { it.storageId == "usb" }.connected)
        } finally { db.close() }
    }
    @Test fun directFileChangesUpdateOnlyAffectedRowsAndChunkLargeDeletes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        try {
            val template = CatalogMediaRow(
                uri = "content://media/external_primary/images/media/1",
                mediaId = 1, name = "photo.jpg", mimeType = "image/jpeg",
                kind = "IMAGE", dateTakenMillis = 1L, width = 100, height = 100,
                durationMillis = 0L, album = "DCIM", relativePath = "DCIM/",
                sizeBytes = 50L, dateModifiedMillis = 1L,
                storageId = "external_primary", storageName = "Phone",
                folderTarget = "/phone/DCIM", sourcePath = "/phone/DCIM/photo.jpg",
            )
            val existing = (1..450).map { id ->
                template.copy(uri = "content://media/external_primary/images/media/$id", mediaId = id.toLong())
            }
            val dao = db.catalog()
            dao.upsertMedia(existing)
            val created = template.copy(uri = "content://media/external_primary/images/media/451",
                mediaId = 451, name = "edited.jpg")
            dao.applyMediaChanges(listOf(created), existing.take(449).map { it.uri })
            assertEquals(setOf(existing.last().uri, created.uri), dao.allMedia().map { it.uri }.toSet())
            assertEquals("edited.jpg", dao.allMedia().single { it.uri == created.uri }.name)
            // A repeated delete is safe and never removes unrelated originals.
            dao.applyMediaChanges(emptyList(), existing.take(449).map { it.uri })
            assertEquals(2, dao.allMedia().size)
        } finally { db.close() }
    }

    @Test fun appOwnedRenameMoveAndRestoreKeepUnrelatedCatalogueRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        try {
            val source = CatalogMediaRow(
                uri = "content://media/external_primary/images/media/21",
                mediaId = 21L, name = "original.jpg", mimeType = "image/jpeg",
                kind = "IMAGE", dateTakenMillis = 1L, width = 100, height = 100,
                durationMillis = 0L, album = "Camera", relativePath = "DCIM/Camera/",
                sizeBytes = 40L, dateModifiedMillis = 1L,
                storageId = "external_primary", storageName = "Phone",
                folderTarget = "/phone/DCIM/Camera",
                sourcePath = "/phone/DCIM/Camera/original.jpg",
            )
            val untouched = source.copy(uri = "content://media/external_primary/images/media/22",
                mediaId = 22L, name = "other.jpg")
            val dao = db.catalog()
            dao.upsertMedia(listOf(source, untouched))

            // MediaStore may retain the same URI on rename.
            val renamed = source.copy(name = "renamed.jpg",
                sourcePath = "/phone/DCIM/Camera/renamed.jpg")
            dao.applyMediaChanges(listOf(renamed), emptyList())
            assertEquals(renamed, dao.allMedia().single { it.uri == source.uri })

            // SAF/cross-volume moves may allocate a new URI. Delete only the
            // old identity after the physical operation has succeeded.
            val moved = renamed.copy(
                uri = "content://media/usb/images/media/81", mediaId = 81L,
                storageId = "usb", storageName = "USB", album = "USB · Trip",
                relativePath = "Trip/", folderTarget = "/usb/Trip",
                sourcePath = "/usb/Trip/renamed.jpg",
            )
            dao.applyMediaChanges(listOf(moved), listOf(source.uri))
            assertEquals(setOf(moved.uri, untouched.uri), dao.allMedia().map { it.uri }.toSet())

            // Restore can allocate yet another URI. No old URI or unrelated
            // file is deleted by the catalogue mutation.
            val restored = source.copy(
                uri = "content://media/external_primary/images/media/91", mediaId = 91L,
            )
            dao.applyMediaChanges(listOf(restored), listOf(moved.uri))
            assertEquals(setOf(restored.uri, untouched.uri), dao.allMedia().map { it.uri }.toSet())
            assertEquals(untouched, dao.allMedia().single { it.uri == untouched.uri })
        } finally { db.close() }
    }

}

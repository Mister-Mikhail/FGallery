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
}

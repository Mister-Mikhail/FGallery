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
}

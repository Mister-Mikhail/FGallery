package com.mistermikhail.fgallery

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mistermikhail.fgallery.data.CatalogDatabase
import com.mistermikhail.fgallery.data.CatalogMediaRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualDuplicateDatabaseTest {
    @Test fun visualHashIsCachedOnlyForStableEligibleRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        try {
            val photo = CatalogMediaRow(
                uri = "content://media/external_primary/images/media/100",
                mediaId = 100, name = "photo.jpg", mimeType = "image/jpeg",
                kind = "IMAGE", dateTakenMillis = 100, width = 2400, height = 1600,
                durationMillis = 0, album = "Camera", relativePath = "DCIM/",
                sizeBytes = 400_000, dateModifiedMillis = 200,
                storageId = "external_primary", storageName = "Phone",
                folderTarget = "/phone/DCIM", sourcePath = "/phone/DCIM/photo.jpg",
            )
            val raw = photo.copy(uri = "content://media/raw", kind = "RAW", name = "frame.dng")
            val gif = photo.copy(uri = "content://media/gif", name = "animation.gif")
            val recycled = photo.copy(uri = "content://media/bin", recycled = true)
            val dao = db.catalog()
            dao.upsertMedia(listOf(photo, raw, gif, recycled))
            assertEquals(listOf(photo.uri), dao.pendingVisualHashCandidates("", 20).map { it.uri })
            assertEquals(0, dao.setVisualHash(photo.uri, 400_000, 199, null, "0000000000000000"))
            assertEquals(1, dao.setVisualHash(photo.uri, 400_000, 200, null, "0000000000000000"))
            assertEquals(0, dao.setVisualHash(photo.uri, 400_000, 200, null, "1111111111111111"))
            assertEquals("0000000000000000", dao.allMedia().first { it.uri == photo.uri }.perceptualHash)
            assertTrue(dao.pendingVisualHashCandidates("", 20).isEmpty())

            // A new quick digest invalidates the visual hash. An in-flight
            // visual decode started under the old quick hash cannot overwrite it.
            assertEquals(1, dao.setQuickHash(photo.uri, 400_000, 200, "edge1"))
            assertNull(dao.allMedia().first { it.uri == photo.uri }.perceptualHash)
            assertEquals(0, dao.setVisualHash(photo.uri, 400_000, 200, null, "stale"))
            assertEquals(1, dao.setVisualHash(photo.uri, 400_000, 200, "edge1", "2222222222222222"))
            dao.applyMediaChanges(listOf(photo.copy(sizeBytes = 401_000)), emptyList())
            assertNull(dao.allMedia().first { it.uri == photo.uri }.perceptualHash)
        } finally { db.close() }
    }
}

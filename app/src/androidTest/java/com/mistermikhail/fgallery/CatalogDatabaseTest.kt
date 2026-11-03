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

    @Test fun fingerprintMetadataSurvivesDeltaButInvalidatesWhenFileChanges() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        try {
            val original = CatalogMediaRow(
                uri = "content://media/external_primary/images/media/100",
                mediaId = 100L, name = "photo.jpg", mimeType = "image/jpeg",
                kind = "IMAGE", dateTakenMillis = 100L, width = 100, height = 100,
                durationMillis = 0L, album = "Camera", relativePath = "DCIM/",
                sizeBytes = 1000L, dateModifiedMillis = 200L,
                storageId = "external_primary", storageName = "Phone",
                folderTarget = "/phone/DCIM", sourcePath = "/phone/DCIM/photo.jpg",
                quickHash = "edge", fullHash = "sha256",
                perceptualHash = "visual", exifCamera = "Canon",
            )
            val dao = db.catalog()
            val volume = CatalogVolumeRow("external_primary", "Phone", 5L, "v1", 200L, true)
            dao.replaceSnapshot(listOf(original), emptyList(), listOf(volume))

            // Incremental metadata query does not include cached fingerprints.
            val renamed = original.copy(name = "renamed.jpg", quickHash = null,
                fullHash = null, perceptualHash = null, exifCamera = null)
            dao.synchronizeSnapshot(listOf(renamed), emptyList(), listOf(volume))
            val afterRename = dao.allMedia().single()
            assertEquals("renamed.jpg", afterRename.name)
            assertEquals("sha256", afterRename.fullHash)
            assertEquals("visual", afterRename.perceptualHash)

            // A changed file cannot inherit hashes from its previous bytes.
            dao.synchronizeSnapshot(listOf(renamed.copy(sizeBytes = 1001L)),
                emptyList(), listOf(volume))
            val changed = dao.allMedia().single()
            assertNull(changed.quickHash)
            assertNull(changed.fullHash)
            assertNull(changed.perceptualHash)
            assertNull(changed.exifCamera)
        } finally { db.close() }
    }

    @Test fun fingerprintWritesAreConditionalAndNewEdgesInvalidateStaleFullHashes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        try {
            val original = CatalogMediaRow(
                uri = "content://media/external_primary/images/media/500",
                mediaId = 500L, name = "image.jpg", mimeType = "image/jpeg",
                kind = "IMAGE", dateTakenMillis = 1L, width = 40, height = 30,
                durationMillis = 0L, album = "Camera", relativePath = "DCIM/",
                sizeBytes = 400L, dateModifiedMillis = 100L,
                storageId = "external_primary", storageName = "Phone",
                folderTarget = "/phone/DCIM", sourcePath = "/phone/DCIM/image.jpg",
            )
            val dao = db.catalog()
            dao.upsertMedia(listOf(original))
            assertEquals(0, dao.setQuickHash(original.uri, 400L, 99L, "old"))
            assertNull(dao.allMedia().single().quickHash)
            assertEquals(1, dao.setQuickHash(original.uri, 400L, 100L, "edge1"))
            assertEquals(0, dao.setFullHash(original.uri, 400L, 100L, "wrong", "bad"))
            assertEquals(1, dao.setFullHash(original.uri, 400L, 100L, "edge1", "full1"))
            assertEquals("full1", dao.allMedia().single().fullHash)
            assertEquals(1, dao.setQuickHash(original.uri, 400L, 100L, "edge2"))
            assertNull(dao.allMedia().single().fullHash)
            assertEquals("edge2", dao.allMedia().single().quickHash)
            // A newer scan supersedes the row while a large RAW is hashing.
            dao.applyMediaChanges(listOf(original.copy(sizeBytes = 401L)), emptyList())
            assertEquals(0, dao.setFullHash(original.uri, 400L, 100L, "edge2", "stale"))
            assertNull(dao.allMedia().single().fullHash)
        } finally { db.close() }
    }

    @Test fun sqliteCandidatePlannerMatchesSafeJvmPlanner() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        try {
            val template = CatalogMediaRow(
                uri = "media://1", mediaId = 1, name = "photo.jpg",
                mimeType = "image/jpeg", kind = "IMAGE", dateTakenMillis = 100,
                width = 100, height = 100, durationMillis = 0,
                album = "Camera", relativePath = "DCIM/",
                sizeBytes = 100, dateModifiedMillis = 100,
                storageId = "external_primary", storageName = "Phone",
                folderTarget = "/phone/DCIM", sourcePath = "/phone/DCIM/photo.jpg",
            )
            fun row(id: Int, size: Long, quick: String? = null,
                    full: String? = null, recycled: Boolean = false) =
                template.copy(uri = "media://" + id, mediaId = id.toLong(),
                    sizeBytes = size, quickHash = quick,
                    fullHash = full, recycled = recycled)
            val rows = listOf(
                row(1, 100), row(2, 100), row(3, 100, "edge", "full"),
                row(4, 200, "match"), row(5, 200, "match", "full"),
                row(6, 300), row(7, 100, recycled = true),
                row(8, 400, ""), row(9, 400, ""),
                row(10, 500, "other"), row(11, 500, "different"),
            )
            val dao = db.catalog()
            dao.upsertMedia(rows)
            assertEquals(
                com.mistermikhail.fgallery.data.quickHashCandidates(rows).map { it.uri },
                dao.pendingQuickHashCandidates().map { it.uri },
            )
            assertEquals(
                com.mistermikhail.fgallery.data.fullHashCandidates(rows).map { it.uri },
                dao.pendingFullHashCandidates().map { it.uri },
            )
            assertEquals(1, dao.setQuickHash(rows[0].uri, 100L, 100L, "edge"))
            assertFalse(dao.pendingQuickHashCandidates().any { it.uri == rows[0].uri })
            assertTrue(dao.pendingFullHashCandidates().any { it.uri == rows[0].uri })
        } finally { db.close() }
    }

    @Test fun seekableParcelDescriptorUsesSameEdgeDigestAsLocalFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File.createTempFile("fgallery-fd", ".raw", context.cacheDir)
        try {
            file.writeBytes(ByteArray(180_000) { (it * 19).toByte() })
            val expected = com.mistermikhail.fgallery.data.DuplicateFingerprints.quick(file)
            val descriptor = android.os.ParcelFileDescriptor.open(
                file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val actual = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
                com.mistermikhail.fgallery.data.DuplicateFingerprints.quick(it.channel)
            }
            assertEquals(expected, actual)
        } finally { file.delete() }
    }


    @Test fun restoredFileDropsOldBinUriWithoutAffectingUnrelatedRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        try {
            val bin = CatalogMediaRow(
                uri = "file:///storage/FGalleryBin/old.jpg",
                mediaId = 1L, name = "old.jpg", mimeType = "image/jpeg",
                kind = "IMAGE", dateTakenMillis = 100L, width = 100, height = 100,
                durationMillis = 0L, album = "Bin", relativePath = "FGalleryBin/",
                sizeBytes = 1000L, dateModifiedMillis = 200L,
                storageId = "external_primary", storageName = "Phone",
                folderTarget = "/storage/FGalleryBin",
                sourcePath = "/storage/FGalleryBin/old.jpg", recycled = true,
            )
            val other = bin.copy(uri = "file:///storage/DCIM/other.jpg", recycled = false)
            val restored = bin.copy(uri = "file:///storage/DCIM/old.jpg",
                folderTarget = "/storage/DCIM", relativePath = "DCIM/", recycled = false)
            val dao = db.catalog()
            dao.upsertMedia(listOf(bin, other))
            // Mirrors the completed physical restore followed by refresh/upsert.
            dao.applyMediaChanges(emptyList(), setOf(bin.uri))
            dao.applyMediaChanges(listOf(restored), emptySet())
            assertEquals(setOf(other.uri, restored.uri), dao.allMedia().map { it.uri }.toSet())
            assertTrue(dao.allMedia().none { it.recycled })
        } finally { db.close() }
    }



    @Test fun unchangedHashedRowsKeepTheirSqliteIdentityAcrossIncrementalScans() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
        try {
            val cached = CatalogMediaRow(
                uri = "media://cached", mediaId = 1L, name = "photo.jpg",
                mimeType = "image/jpeg", kind = "IMAGE", dateTakenMillis = 100L,
                width = 1200, height = 800, durationMillis = 0L,
                album = "DCIM", relativePath = "DCIM/", sizeBytes = 1000L,
                dateModifiedMillis = 200L, storageId = "external_primary",
                storageName = "Phone", folderTarget = "/phone/DCIM",
                sourcePath = "/phone/DCIM/photo.jpg",
                quickHash = "edge", fullHash = "full", perceptualHash = "visual",
            )
            // A second row prevents SQLite from reusing the deleted maximum
            // rowid if a regression reintroduces INSERT OR REPLACE.
            val untouched = cached.copy(uri = "media://sentinel", mediaId = 2L)
            val dao = db.catalog()
            dao.upsertMedia(listOf(cached, untouched))
            fun rowId(): Long = db.openHelper.readableDatabase
                .query("SELECT rowid FROM catalog_media WHERE uri = 'media://cached'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getLong(0)
                }
            val before = rowId()
            val scan = cached.copy(quickHash = null, fullHash = null, perceptualHash = null)
            dao.synchronizeSnapshot(listOf(scan, untouched), emptyList(), emptyList())
            assertEquals("Unchanged file must not be REPLACEd by a hash-free scan", before, rowId())
            assertEquals(cached, dao.allMedia().single { it.uri == cached.uri })

            // Real metadata changes still propagate without dropping the
            // fingerprint when size, mtime and volume are unchanged.
            dao.synchronizeSnapshot(
                listOf(scan.copy(name = "renamed.jpg"), untouched),
                emptyList(), emptyList(),
            )
            val renamed = dao.allMedia().single { it.uri == cached.uri }
            assertEquals("renamed.jpg", renamed.name)
            assertEquals("full", renamed.fullHash)
            assertEquals("visual", renamed.perceptualHash)
        } finally { db.close() }
    }

}

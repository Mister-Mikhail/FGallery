package com.mistermikhail.fgallery.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

/**
 * Durable read-only catalogue index. Original media stays exclusively on its
 * existing volume/SAF provider; SQLite never stores or modifies media bytes.
 *
 * Hash columns are reserved for approved Stage 6, without computing them.
 */
@Entity(
    tableName = "catalog_media",
    indices = [Index(value = ["storageId", "relativePath"]), Index(value = ["dateModifiedMillis"])],
)
internal data class CatalogMediaRow(
    @PrimaryKey val uri: String,
    val mediaId: Long,
    val name: String,
    val mimeType: String?,
    val kind: String,
    val dateTakenMillis: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long,
    val album: String,
    val relativePath: String,
    val sizeBytes: Long,
    val dateModifiedMillis: Long,
    val storageId: String,
    val storageName: String,
    val folderTarget: String,
    val sourcePath: String,
    val quickHash: String? = null,
    val fullHash: String? = null,
    val perceptualHash: String? = null,
    val exifCamera: String? = null,
    val recycled: Boolean = false,
)

@Entity(tableName = "catalog_folders", indices = [Index(value = ["storageId"])])
internal data class CatalogFolderRow(
    @PrimaryKey val key: String,
    val storageId: String,
    val relativePath: String,
    val lastModifiedMillis: Long,
    val lastScannedMillis: Long,
)

@Entity(tableName = "catalog_volumes")
internal data class CatalogVolumeRow(
    @PrimaryKey val storageId: String,
    val displayName: String,
    val mediaStoreGeneration: Long,
    val mediaStoreVersion: String,
    val lastScannedMillis: Long,
    val connected: Boolean,
)

/**
 * Stage 6 fingerprints survive ordinary metadata refreshes and same-URI renames,
 * but must be invalidated when the underlying bytes may have changed.
 * An incoming non-null freshly computed fingerprint always wins.
 */
internal fun retainUnchangedFingerprints(
    old: CatalogMediaRow?,
    incoming: CatalogMediaRow,
): CatalogMediaRow {
    if (old == null || old.sizeBytes != incoming.sizeBytes ||
        old.dateModifiedMillis != incoming.dateModifiedMillis ||
        old.storageId != incoming.storageId
    ) return incoming
    // A newly computed edge digest can reveal a same-size/same-mtime
    // replacement. Never attach the previous full digest to new bytes.
    if (incoming.quickHash != null && incoming.quickHash != old.quickHash) return incoming
    return incoming.copy(
        quickHash = incoming.quickHash ?: old.quickHash,
        fullHash = incoming.fullHash ?: old.fullHash,
        perceptualHash = incoming.perceptualHash ?: old.perceptualHash,
        exifCamera = incoming.exifCamera ?: old.exifCamera,
    )
}

@Dao
internal interface CatalogDao {
    @Query("SELECT * FROM catalog_media")
    suspend fun allMedia(): List<CatalogMediaRow>

    /** Query only pending candidates rather than materializing the whole Room
     * catalogue on every idle batch. Do not LIMIT: inaccessible paths at the
     * front would otherwise starve valid files farther down the index. */
    @Query("""
        SELECT m.* FROM catalog_media m
        JOIN (SELECT sizeBytes FROM catalog_media
              WHERE recycled = 0 AND sizeBytes > 0
              GROUP BY sizeBytes HAVING COUNT(*) > 1) collisions
          ON m.sizeBytes = collisions.sizeBytes
        WHERE m.recycled = 0 AND m.quickHash IS NULL
        ORDER BY m.sizeBytes, m.uri
    """)
    suspend fun pendingQuickHashCandidates(): List<CatalogMediaRow>

    /** Matching nonempty edge digests are candidates, never proof of equality. */
    @Query("""
        SELECT m.* FROM catalog_media m
        JOIN (SELECT sizeBytes, quickHash FROM catalog_media
              WHERE recycled = 0 AND sizeBytes > 0
                AND quickHash IS NOT NULL AND quickHash != ''
              GROUP BY sizeBytes, quickHash HAVING COUNT(*) > 1) collisions
          ON m.sizeBytes = collisions.sizeBytes AND m.quickHash = collisions.quickHash
        WHERE m.recycled = 0 AND m.fullHash IS NULL
        ORDER BY m.sizeBytes, m.uri
    """)
    suspend fun pendingFullHashCandidates(): List<CatalogMediaRow>

    /** Keyset paging: successful hashes remove rows from this query without
     * skipping later candidates. Missing posters can be retried on next pass. */
    @Query("""
        SELECT * FROM catalog_media
        WHERE recycled = 0 AND kind = 'IMAGE'
          AND width >= 64 AND height >= 64
          AND LOWER(name) NOT LIKE '%.gif'
          AND perceptualHash IS NULL AND uri > :afterUri
        ORDER BY uri LIMIT :limit
    """)
    suspend fun pendingVisualHashCandidates(afterUri: String, limit: Int): List<CatalogMediaRow>

    @Query("SELECT * FROM catalog_volumes")
    suspend fun allVolumes(): List<CatalogVolumeRow>

    @Query("SELECT * FROM catalog_folders")
    suspend fun allFolders(): List<CatalogFolderRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(items: List<CatalogMediaRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolders(items: List<CatalogFolderRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVolumes(items: List<CatalogVolumeRow>)

    @Query("SELECT * FROM catalog_media WHERE uri IN (:uris)")
    suspend fun mediaByUris(uris: List<String>): List<CatalogMediaRow>

    /** Conditional writes avoid attaching a digest to a row changed by a concurrent scan. */
    @Query("UPDATE catalog_media SET quickHash = :hash, fullHash = CASE WHEN quickHash = :hash THEN fullHash ELSE NULL END, perceptualHash = CASE WHEN quickHash = :hash THEN perceptualHash ELSE NULL END, exifCamera = CASE WHEN quickHash = :hash THEN exifCamera ELSE NULL END WHERE uri = :uri AND sizeBytes = :size AND dateModifiedMillis = :modified AND recycled = 0")
    suspend fun setQuickHash(uri: String, size: Long, modified: Long, hash: String): Int

    @Query("UPDATE catalog_media SET fullHash = :hash WHERE uri = :uri AND sizeBytes = :size AND dateModifiedMillis = :modified AND quickHash = :quick AND recycled = 0")
    suspend fun setFullHash(uri: String, size: Long, modified: Long, quick: String, hash: String): Int

    /** A visual poster must not be attached to an updated row or changed
     * edge digest. It is only a prefilter, never duplicate evidence. */
    @Query("""
        UPDATE catalog_media SET perceptualHash = :hash
        WHERE uri = :uri AND sizeBytes = :size AND dateModifiedMillis = :modified
          AND recycled = 0 AND perceptualHash IS NULL
          AND ((:edge IS NULL AND quickHash IS NULL) OR quickHash = :edge)
    """)
    suspend fun setVisualHash(
        uri: String, size: Long, modified: Long, edge: String?, hash: String,
    ): Int

    @Query("DELETE FROM catalog_media WHERE uri IN (:uris)")
    suspend fun deleteMedia(uris: List<String>)

    /** Apply an app-owned file operation immediately, independently of the next scan.
     * Preserve unrelated rows and split deletes to stay under SQLite bind limits. */
    @Transaction
    suspend fun applyMediaChanges(upserts: List<CatalogMediaRow>, removedUris: Collection<String>) {
        removedUris.chunked(400).forEach { deleteMedia(it) }
        if (upserts.isNotEmpty()) {
            val previous = upserts.map { it.uri }.chunked(400)
                .flatMap { mediaByUris(it) }.associateBy { it.uri }
            upsertMedia(upserts.map { retainUnchangedFingerprints(previous[it.uri], it) })
        }
    }

    @Query("DELETE FROM catalog_media")
    suspend fun clearMedia()

    @Query("DELETE FROM catalog_folders")
    suspend fun clearFolders()

    @Query("DELETE FROM catalog_volumes")
    suspend fun clearVolumes()

    @Query("DELETE FROM catalog_folders WHERE key IN (:keys)")
    suspend fun deleteFolders(keys: List<String>)

    @Query("DELETE FROM catalog_volumes WHERE storageId IN (:ids)")
    suspend fun deleteVolumes(ids: List<String>)

    /**
     * Small-delta writes, still in one transaction. Never rewrite every media
     * row after every ContentObserver event. Deletes are chunked for SQLite's
     * bind-parameter limit.
     */
    @Transaction
    suspend fun synchronizeSnapshot(
        media: List<CatalogMediaRow>,
        folders: List<CatalogFolderRow>,
        volumes: List<CatalogVolumeRow>,
    ) {
        // A disconnected USB/SD volume must retain its catalogue and folder
        // metadata. Only its connected flag changes. Otherwise a refresh while
        // unplugged erases the cache and the next mount has no history to reuse.
        // Once reconnected, absent rows can be removed by the fresh scan.
        val disconnectedIds = volumes.asSequence().filterNot { it.connected }
            .mapTo(hashSetOf()) { it.storageId }
        val oldMedia = allMedia().associateBy { it.uri }
        val current = media.associateBy { it.uri }.toMutableMap()
        oldMedia.values.filter { it.storageId in disconnectedIds }
            .forEach { current.putIfAbsent(it.uri, it) }
        oldMedia.keys.filterNot { it in current }.chunked(400).forEach { deleteMedia(it) }
        upsertMedia(current.values
            .map { retainUnchangedFingerprints(oldMedia[it.uri], it) }
            .filter { oldMedia[it.uri] != it })

        val oldFolders = allFolders().associateBy { it.key }
        val currentFolders = folders.associateBy { it.key }.toMutableMap()
        oldFolders.values.filter { it.storageId in disconnectedIds }
            .forEach { currentFolders.putIfAbsent(it.key, it) }
        oldFolders.keys.filterNot { it in currentFolders }.chunked(400).forEach { deleteFolders(it) }
        upsertFolders(currentFolders.values.filter { oldFolders[it.key] != it })

        val oldVolumes = allVolumes().associateBy { it.storageId }
        val currentVolumes = volumes.associateBy { it.storageId }
        oldVolumes.keys.filterNot { it in currentVolumes }.chunked(400).forEach { deleteVolumes(it) }
        upsertVolumes(volumes.filter { oldVolumes[it.storageId] != it })
    }

    @Transaction
    suspend fun replaceSnapshot(
        media: List<CatalogMediaRow>,
        folders: List<CatalogFolderRow>,
        volumes: List<CatalogVolumeRow>,
    ) {
        clearMedia()
        clearFolders()
        clearVolumes()
        upsertMedia(media)
        upsertFolders(folders)
        upsertVolumes(volumes)
    }
}

@Database(
    entities = [CatalogMediaRow::class, CatalogFolderRow::class, CatalogVolumeRow::class],
    version = 1,
    exportSchema = true,
)
internal abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao

    companion object {
        @Volatile private var instance: CatalogDatabase? = null

        fun get(context: Context): CatalogDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CatalogDatabase::class.java,
                    "fgallery_catalog_v1.db",
                ).build().also { instance = it }
            }
    }
}

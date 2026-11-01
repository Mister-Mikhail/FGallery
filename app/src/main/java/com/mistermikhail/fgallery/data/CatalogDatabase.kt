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

@Dao
internal interface CatalogDao {
    @Query("SELECT * FROM catalog_media")
    suspend fun allMedia(): List<CatalogMediaRow>

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

    @Query("DELETE FROM catalog_media WHERE uri IN (:uris)")
    suspend fun deleteMedia(uris: List<String>)

    @Query("DELETE FROM catalog_media")
    suspend fun clearMedia()

    @Query("DELETE FROM catalog_folders")
    suspend fun clearFolders()

    @Query("DELETE FROM catalog_volumes")
    suspend fun clearVolumes()

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

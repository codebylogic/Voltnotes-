package com.shivam.vaultnotes.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vault_config")
data class VaultConfigEntity(
    @PrimaryKey val id: Int = 0,
    val vaultUriString: String,
    val isSafTree: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "directory_sort_orders")
data class DirectorySortOrderEntity(
    @PrimaryKey val compositeKey: String, // "$directoryPath|$fileName"
    val directoryPath: String,            // "" for root, or "Study", "Study/Sub"
    val fileName: String,                 // e.g. "Physics Notes.html" or "Study"
    val sortIndex: Int
)

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_config WHERE id = 0 LIMIT 1")
    fun observeVaultConfig(): Flow<VaultConfigEntity?>

    @Query("SELECT * FROM vault_config WHERE id = 0 LIMIT 1")
    suspend fun getVaultConfig(): VaultConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVaultConfig(config: VaultConfigEntity)

    @Query("SELECT * FROM directory_sort_orders WHERE directoryPath = :directoryPath ORDER BY sortIndex ASC")
    fun observeSortOrdersForDirectory(directoryPath: String): Flow<List<DirectorySortOrderEntity>>

    @Query("SELECT * FROM directory_sort_orders WHERE directoryPath = :directoryPath ORDER BY sortIndex ASC")
    suspend fun getSortOrdersForDirectory(directoryPath: String): List<DirectorySortOrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSortOrders(orders: List<DirectorySortOrderEntity>)

    @Query("DELETE FROM directory_sort_orders WHERE directoryPath = :directoryPath AND fileName = :fileName")
    suspend fun deleteSortOrderItem(directoryPath: String, fileName: String)

    @Query("DELETE FROM directory_sort_orders WHERE directoryPath = :directoryPath OR directoryPath LIKE :prefix || '/%'")
    suspend fun deleteSortOrdersUnderPath(directoryPath: String, prefix: String = directoryPath)
}

@Database(
    entities = [VaultConfigEntity::class, DirectorySortOrderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vaultnotes_metadata.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

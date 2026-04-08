package com.optic.tv.db

import androidx.room.*

@Entity(tableName = "channels", indices = [Index(value = arrayOf("type", "groupName"))])
data class ChannelEntity(
    @PrimaryKey
    val url: String,
    val name: String,
    val logo: String,
    val userAgent: String,
    val referrer: String,
    val type: String,
    val groupName: String,
    val isFavorite: Boolean = false
)

@Dao
interface ChannelsDao {
    @Query("SELECT * FROM channels WHERE type = :type AND groupName = :groupName")
    suspend fun getChannelsByGroup(type: String, groupName: String): List<ChannelEntity>

    @Query("SELECT DISTINCT groupName FROM channels WHERE type = :type")
    suspend fun getGroupNames(type: String): List<String>

    @Query("SELECT groupName FROM channels WHERE url = :url LIMIT 1")
    suspend fun getGroupNameByUrl(url: String): String?

    @Query("SELECT COUNT(*) FROM channels WHERE type = :type AND groupName = :groupName")
    suspend fun getChannelCountByGroup(type: String, groupName: String): Int

    @Query("SELECT COUNT(*) FROM channels WHERE type = :type")
    suspend fun getTotalChannelCount(type: String): Int

    @Query("SELECT * FROM channels WHERE type = :type")
    suspend fun getAllChannels(type: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE isFavorite = 1")
    suspend fun getFavorites(): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :isFav WHERE url = :url")
    suspend fun updateFavorite(url: String, isFav: Boolean)

    @Query("DELETE FROM channels")
    suspend fun clearAll()
}

@Entity(tableName = "continue_watching")
data class ContinueWatchingEntity(
    @PrimaryKey val url: String,
    val channelName: String,
    val progressMs: Long,
    val durationMs: Long,
    val contentType: String,
    val updatedAt: Long
)

@Dao
interface ContinueWatchingDao {
    @Query("SELECT * FROM continue_watching ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ContinueWatchingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: ContinueWatchingEntity)
}

@Database(entities = arrayOf(ChannelEntity::class, ContinueWatchingEntity::class), version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelsDao(): ChannelsDao
    abstract fun continueWatchingDao(): ContinueWatchingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iptv_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

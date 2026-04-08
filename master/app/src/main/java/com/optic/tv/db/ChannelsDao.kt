package com.optic.tv.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

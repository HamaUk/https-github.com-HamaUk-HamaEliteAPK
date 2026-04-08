package com.optic.tv.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ContinueWatchingDao {
    @Query("SELECT * FROM continue_watching ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ContinueWatchingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: ContinueWatchingEntity): Unit
}

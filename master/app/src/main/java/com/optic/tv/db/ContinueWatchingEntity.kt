package com.optic.tv.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "continue_watching")
data class ContinueWatchingEntity(
    @PrimaryKey val url: String,
    val channelName: String,
    val progressMs: Long,
    val durationMs: Long,
    val contentType: String,
    val updatedAt: Long
)

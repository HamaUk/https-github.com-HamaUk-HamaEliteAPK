package com.optic.tv.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "channels", indices = [Index(value = ["type", "groupName"])])
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

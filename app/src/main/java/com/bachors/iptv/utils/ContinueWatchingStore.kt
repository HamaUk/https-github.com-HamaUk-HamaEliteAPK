package com.bachors.iptv.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ContinueWatchingEntry(
    val url: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val contentType: String = "vod"
)

object ContinueWatchingStore {
    private const val MAX_ITEMS = 40
    private val gson = Gson()

    fun getAll(ctx: Context): List<ContinueWatchingEntry> {
        val raw = SharedPrefManager(ctx).getSpString(SharedPrefManager.SP_CONTINUE_WATCHING)
        if (raw.isBlank() || raw == "[]") return emptyList()
        return try {
            val type = object : TypeToken<List<ContinueWatchingEntry>>() {}.type
            gson.fromJson<List<ContinueWatchingEntry>>(raw, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }.sortedByDescending { it.updatedAt }.take(MAX_ITEMS)
    }

    fun getResumePositionMs(ctx: Context, url: String): Long {
        val u = url.trim()
        return getAll(ctx).firstOrNull { it.url == u }?.positionMs ?: -1L
    }

    fun upsert(
        ctx: Context,
        url: String,
        title: String,
        positionMs: Long,
        durationMs: Long,
        contentType: String
    ) {
        if (url.isBlank() || durationMs <= 0) return
        if (positionMs < 5_000L) return
        if (positionMs >= durationMs - 10_000L) {
            remove(ctx, url)
            return
        }
        val list = getAll(ctx).filter { it.url != url.trim() }.toMutableList()
        list.add(
            0,
            ContinueWatchingEntry(
                url = url.trim(),
                title = title,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
                contentType = contentType
            )
        )
        persist(ctx, list.take(MAX_ITEMS))
    }

    fun remove(ctx: Context, url: String) {
        val u = url.trim()
        persist(ctx, getAll(ctx).filter { it.url != u })
    }

    private fun persist(ctx: Context, list: List<ContinueWatchingEntry>) {
        SharedPrefManager(ctx).saveSPString(
            SharedPrefManager.SP_CONTINUE_WATCHING,
            gson.toJson(list)
        )
    }
}

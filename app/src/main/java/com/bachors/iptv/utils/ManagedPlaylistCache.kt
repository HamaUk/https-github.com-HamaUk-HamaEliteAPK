package com.bachors.iptv.utils

import android.content.Context
import com.bachors.iptv.models.ChannelsData
import com.google.gson.Gson
import java.io.File

/**
 * Caches [ManagedPlaylist] from Firebase sync on disk. Icons and names come from admin `managedPlaylist.items`.
 */
object ManagedPlaylistCache {
    private const val FILE_NAME = "managed_playlist_cache.json"
    private val gson = Gson()

    fun cacheFile(ctx: Context): File = File(ctx.cacheDir, FILE_NAME)

    fun clear(ctx: Context) {
        try {
            val f = cacheFile(ctx)
            if (f.exists()) f.delete()
        } catch (_: Exception) { }
    }

    fun persistFromSync(ctx: Context, mp: ManagedPlaylist?) {
        val items = mp?.items
        if (items.isNullOrEmpty()) {
            clear(ctx)
            return
        }
        try {
            cacheFile(ctx).writeText(gson.toJson(mp), Charsets.UTF_8)
        } catch (_: Exception) {
            clear(ctx)
        }
    }

    fun hasCachedItems(ctx: Context): Boolean {
        val f = cacheFile(ctx)
        if (!f.exists() || f.length() <= 2L) return false
        return try {
            val mp = gson.fromJson(f.readText(Charsets.UTF_8), ManagedPlaylist::class.java)
            !mp?.items.isNullOrEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fills [groupMap] from managed items (skips hidden). Logos use [ManagedChannelItem.logo] for the app list / Picasso.
     */
    fun fillGroupMap(
        ctx: Context,
        currentType: String,
        groupMap: MutableMap<String, MutableList<ChannelsData>>
    ): Boolean {
        val f = cacheFile(ctx)
        if (!f.exists() || f.length() <= 2L) return false
        val mp = try {
            gson.fromJson(f.readText(Charsets.UTF_8), ManagedPlaylist::class.java)
        } catch (_: Exception) {
            null
        } ?: return false
        val items = mp.items ?: return false
        if (items.isEmpty()) return false

        groupMap.clear()
        val sortedEntries = items.entries.sortedWith(
            compareBy<Map.Entry<String, ManagedChannelItem>> { it.value.order ?: Int.MAX_VALUE }
                .thenBy { it.key }
        )
        for ((_, item) in sortedEntries) {
            if (item.hidden == true) continue
            val url = item.url?.trim().orEmpty()
            if (url.isEmpty()) continue
            val name = item.name?.trim().orEmpty().ifEmpty { "کەناڵ" }
            val group = item.group?.trim().orEmpty().ifEmpty { "گشتی" }
            val logo = item.logo?.trim().orEmpty()
            val resolvedType = item.type?.trim()?.lowercase()?.takeIf { it in setOf("live", "vod", "series") }
                ?: M3uTypeDetect.detectTypeFromUrl(url)
            val typeFilter = when (currentType) {
                "vod", "series" -> currentType
                else -> "live"
            }
            if (resolvedType != typeFilter) continue

            val ch = ChannelsData(
                name = name,
                logo = logo,
                url = url,
                userAgent = "",
                referrer = ""
            )
            val key = "$resolvedType|$group"
            groupMap.getOrPut(key) { mutableListOf() }.add(ch)
        }
        return groupMap.isNotEmpty()
    }
}

package com.optic.tv.utils

import android.content.Context
import com.optic.tv.models.ChannelsData
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

private data class PlaylistCustomOrder(
    @SerializedName("g") val groupOrderByType: MutableMap<String, MutableList<String>> = mutableMapOf(),
    @SerializedName("c") val channelUrlsByTypeAndGroup: MutableMap<String, MutableMap<String, MutableList<String>>> = mutableMapOf(),
    @SerializedName("v") val virtualGroupOrderByType: MutableMap<String, MutableList<String>> = mutableMapOf()
)

object PlaylistOrderStore {
    private val gson = Gson()

    private fun load(ctx: Context): PlaylistCustomOrder {
        val raw = SharedPrefManager(ctx).getSpString(SharedPrefManager.SP_PLAYLIST_CUSTOM_ORDER)
        if (raw.isBlank()) return PlaylistCustomOrder()
        return try {
            val parsed = gson.fromJson(raw, PlaylistCustomOrder::class.java)
            PlaylistCustomOrder(
                groupOrderByType = parsed?.groupOrderByType ?: mutableMapOf(),
                channelUrlsByTypeAndGroup = parsed?.channelUrlsByTypeAndGroup ?: mutableMapOf(),
                virtualGroupOrderByType = parsed?.virtualGroupOrderByType ?: mutableMapOf()
            )
        } catch (_: Exception) {
            PlaylistCustomOrder()
        }
    }

    private fun save(ctx: Context, o: PlaylistCustomOrder) {
        SharedPrefManager(ctx).saveSPString(SharedPrefManager.SP_PLAYLIST_CUSTOM_ORDER, gson.toJson(o))
    }

    fun moveGroupToTop(ctx: Context, typeKey: String, groupDisplayName: String) {
        val o = load(ctx)
        val list = o.groupOrderByType.getOrPut(typeKey) { mutableListOf() }
        list.remove(groupDisplayName)
        list.add(0, groupDisplayName)
        save(ctx, o)
    }

    /** Reorder the three built-in categories (All / Favorites / Continue watching). Keys must match PlaylistActivity constants. */
    fun moveVirtualGroupToTop(ctx: Context, typeKey: String, virtualKey: String, canonicalVirtualKeys: List<String>) {
        if (virtualKey !in canonicalVirtualKeys) return
        val o = load(ctx)
        val list = o.virtualGroupOrderByType.getOrPut(typeKey) { canonicalVirtualKeys.toMutableList() }
        for (k in canonicalVirtualKeys) {
            if (k !in list) list.add(k)
        }
        list.remove(virtualKey)
        list.add(0, virtualKey)
        save(ctx, o)
    }

    fun applyVirtualGroupOrder(ctx: Context, typeKey: String, canonicalVirtualKeys: List<String>): List<String> {
        val stored = load(ctx).virtualGroupOrderByType[typeKey]
        if (stored.isNullOrEmpty()) return canonicalVirtualKeys
        val allowed = canonicalVirtualKeys.toSet()
        val head = stored.filter { it in allowed }
        val tail = canonicalVirtualKeys.filter { it !in head.toSet() }
        return head + tail
    }

    fun moveChannelToTop(ctx: Context, typeKey: String, groupDisplayName: String, url: String) {
        val o = load(ctx)
        val inner = o.channelUrlsByTypeAndGroup.getOrPut(typeKey) { mutableMapOf() }
        val urls = inner.getOrPut(groupDisplayName) { mutableListOf() }
        urls.remove(url)
        urls.add(0, url)
        save(ctx, o)
    }

    fun applyGroupOrder(ctx: Context, typeKey: String, naturalSorted: List<String>): List<String> {
        val preferred = load(ctx).groupOrderByType[typeKey].orEmpty().filter { it in naturalSorted.toSet() }
        val rest = naturalSorted.filter { it !in preferred.toSet() }
        return preferred + rest
    }

    fun applyChannelOrder(
        ctx: Context,
        typeKey: String,
        groupDisplayName: String,
        channels: List<ChannelsData>
    ): List<ChannelsData> {
        val orderedUrls = load(ctx).channelUrlsByTypeAndGroup[typeKey]?.get(groupDisplayName).orEmpty()
        if (orderedUrls.isEmpty()) return channels
        val byUrl = channels.associateBy { it.url }
        val head = orderedUrls.mapNotNull { byUrl[it] }
        val tail = channels.filter { ch -> orderedUrls.none { it == ch.url } }
        return head + tail
    }
}

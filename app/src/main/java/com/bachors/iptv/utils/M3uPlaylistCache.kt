package com.bachors.iptv.utils

import android.content.Context
import java.io.File

object M3uPlaylistCache {
    private const val CACHED_NAME = "m3u_playlist_cached.m3u"

    fun cacheFile(ctx: Context): File = File(ctx.cacheDir, CACHED_NAME)

    fun invalidate(ctx: Context) {
        try {
            cacheFile(ctx).delete()
        } catch (_: Exception) { }
        SharedPrefManager(ctx).saveSPString(SharedPrefManager.SP_M3U_CACHE_URL, "")
    }

    fun hasCacheForUrl(ctx: Context, playlistUrl: String): Boolean {
        val url = playlistUrl.trim()
        if (url.isEmpty() || url.startsWith("file_content:")) return false
        val saved = SharedPrefManager(ctx).getSpString(SharedPrefManager.SP_M3U_CACHE_URL)
        val f = cacheFile(ctx)
        return url == saved && f.exists() && f.length() > 0L
    }

    fun writeFromDownloadedTemp(ctx: Context, playlistUrl: String, tempFile: File) {
        try {
            tempFile.copyTo(cacheFile(ctx), overwrite = true)
            SharedPrefManager(ctx).saveSPString(SharedPrefManager.SP_M3U_CACHE_URL, playlistUrl.trim())
        } catch (_: Exception) { }
    }
}

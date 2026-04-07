package com.optic.tv.utils

import android.content.Context
import android.net.Uri
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object GlobalSync {
    const val FIREBASE_RTDB_BASE = "https://hama-elite-sync-default-rtdb.firebaseio.com/"
    const val SYNC_KEY_GLOBAL = "global"

    fun retrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(FIREBASE_RTDB_BASE)
        .client(AppHttp.client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun worldTimeRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl("http://worldtimeapi.org/")
        .client(AppHttp.client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun applySyncedConfig(ctx: Context, prefs: SharedPrefManager, data: SyncData): Boolean {
        val method = data.method?.trim()?.lowercase() ?: ""
        val ok = when {
            !data.content.isNullOrBlank() -> {
                prefs.saveSPString(
                    SharedPrefManager.SP_M3U_DIRECT,
                    "file_content:${data.content}"
                )
                prefs.saveSPString(SharedPrefManager.SP_PLAYLIST, "[]")
                true
            }
            method == "m3u" && !data.url.isNullOrBlank() -> {
                prefs.saveSPString(SharedPrefManager.SP_M3U_DIRECT, data.url.trim())
                prefs.saveSPString(SharedPrefManager.SP_PLAYLIST, "[]")
                true
            }
            method == "xtream" && !data.server.isNullOrBlank() && !data.user.isNullOrBlank() && !data.pass.isNullOrBlank() -> {
                val m3uUrl = buildXtreamM3uUrl(data.server, data.user, data.pass)
                prefs.saveSPString(SharedPrefManager.SP_M3U_DIRECT, m3uUrl)
                prefs.saveSPString(SharedPrefManager.SP_PLAYLIST, "[]")
                true
            }
            !data.url.isNullOrBlank() -> {
                prefs.saveSPString(SharedPrefManager.SP_M3U_DIRECT, data.url.trim())
                prefs.saveSPString(SharedPrefManager.SP_PLAYLIST, "[]")
                true
            }
            else -> false
        }
        if (ok) {
            prefs.saveSPString(SharedPrefManager.SP_CHANNELS, "[]")
            prefs.saveSPLong(SharedPrefManager.SP_EXPIRY_DATE, data.expiryDate ?: 0L)
            M3uPlaylistCache.invalidate(ctx)
            ManagedPlaylistCache.persistFromSync(ctx, data.managedPlaylist)
        }
        return ok
    }

    private fun normalizeServer(server: String): String {
        val trimmed = server.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "http://$trimmed"
    }

    private fun buildXtreamM3uUrl(server: String, user: String, pass: String): String {
        val cleanServer = normalizeServer(server).trimEnd('/')
        return "$cleanServer/get.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}&type=m3u_plus&output=mpegts"
    }
}

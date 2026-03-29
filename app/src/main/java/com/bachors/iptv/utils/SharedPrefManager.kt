package com.bachors.iptv.utils

import android.content.Context
import android.content.SharedPreferences

class SharedPrefManager(context: Context) {
    companion object {
        const val SP_SS_APP = "spIPTV"
        const val SP_PLAYLIST = "spPlaylist"
        const val SP_CHANNELS = "spChannels"
        const val SP_FAVORITES = "spFavorites"
        const val SP_FILES = "spFiles"
        const val SP_M3U_DIRECT = "spM3uDirect"
        const val SP_CURRENT_URL = "spCurrentUrl"
        const val SP_RESIZE_MODE = "spResizeMode"
        const val SP_AUTOPLAY = "spAutoplay"
        const val SP_BUFFER_SIZE = "spBufferSize"
        const val SP_HW_ACCEL = "spHwAccel"
        const val SP_SAVED_PLAYLISTS = "spSavedPlaylists"
        const val SP_ACTIVE_PLAYLIST_NAME = "spActivePlaylistName"
    }

    private val sp: SharedPreferences = context.getSharedPreferences(SP_SS_APP, Context.MODE_PRIVATE)
    private val spEditor: SharedPreferences.Editor = sp.edit()

    fun saveSPString(keySP: String, value: String) {
        spEditor.putString(keySP, value)
        spEditor.commit()
    }

    fun getSpPlaylist(): String = sp.getString(SP_PLAYLIST, "[]") ?: "[]"

    fun getSpChannels(): String = sp.getString(SP_CHANNELS, "[]") ?: "[]"

    fun getSpFavorites(): String = sp.getString(SP_FAVORITES, "[]") ?: "[]"

    fun getSpFiles(): String = sp.getString(SP_FILES, "[]") ?: "[]"

    fun getSpM3uDirect(): String = sp.getString(SP_M3U_DIRECT, "") ?: ""
    fun getSpCurrentUrl(): String = sp.getString(SP_CURRENT_URL, "") ?: ""

    fun getSpString(key: String): String = sp.getString(key, "") ?: ""

    fun saveSPBoolean(keySP: String, value: Boolean) {
        spEditor.putBoolean(keySP, value)
        spEditor.commit()
    }

    fun getSpBoolean(key: String, default: Boolean = false): Boolean = sp.getBoolean(key, default)

    fun getResizeMode(): String = sp.getString(SP_RESIZE_MODE, "0") ?: "0"

    fun getAutoplay(): Boolean = sp.getBoolean(SP_AUTOPLAY, false)

    fun getBufferSize(): String = sp.getString(SP_BUFFER_SIZE, "medium") ?: "medium"

    fun getHwAccel(): Boolean = sp.getBoolean(SP_HW_ACCEL, true)

    fun getSavedPlaylists(): String = sp.getString(SP_SAVED_PLAYLISTS, "{}") ?: "{}"

    fun getActivePlaylistName(): String = sp.getString(SP_ACTIVE_PLAYLIST_NAME, "") ?: ""
}
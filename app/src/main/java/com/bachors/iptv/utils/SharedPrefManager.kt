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
}
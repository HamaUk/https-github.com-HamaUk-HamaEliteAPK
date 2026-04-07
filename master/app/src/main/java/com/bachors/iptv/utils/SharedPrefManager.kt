package com.bachors.iptv.utils

import android.content.Context
import android.content.SharedPreferences

class SharedPrefManager(context: Context) {

    private val appContext: Context = context.applicationContext

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
        /** Last M3U URL for which `cacheDir/m3u_playlist_cached.m3u` is valid */
        const val SP_M3U_CACHE_URL = "spM3uCacheUrl"
        /** JSON custom order: groups + channels (see [PlaylistOrderStore]) */
        const val SP_PLAYLIST_CUSTOM_ORDER = "spPlaylistCustomOrder"
        /** JSON array of continue-watching entries */
        const val SP_CONTINUE_WATCHING = "spContinueWatching"
        /** 0=auto, 1=720p cap, 2=1080p cap, 3=4K cap (max dimensions / ABR) */
        const val SP_VIDEO_QUALITY_PRESET = "spVideoQualityPreset"
        /** See [com.bachors.iptv.utils.PlayerLauncher] for values (exo, exo_cinema, exo_arena, web_hls, vlc). */
        const val SP_PLAYER_ENGINE = "spPlayerEngine"
        /** Subscription expiry timestamp (Long) */
        const val SP_EXPIRY_DATE = "spExpiryDate"
        /** Last successful Firebase / playlist sync (epoch millis) */
        const val SP_LAST_SYNC_SUCCESS_AT = "spLastSyncSuccessAt"
        /**
         * App UI language: [LANGUAGE_CKB], [LANGUAGE_AR], [LANGUAGE_EN], [LANGUAGE_KMR].
         * Legacy [LANGUAGE_SYSTEM] is migrated to Sorani on read.
         */
        const val SP_APP_LANGUAGE = "spAppLanguage"
        /** Theme: dark | amoled | light */
        const val SP_THEME_MODE = "spThemeMode"
        /**
         * When false (default), sync uses [sync/global] only.
         * When true, sync uses admin [device_assignments] → dedicated [sync/key] if linked; otherwise error.
         */
        const val SP_USE_PRIVATE_PLAYLIST = "spUsePrivatePlaylist"

        const val LANGUAGE_SYSTEM = "system"
        const val LANGUAGE_CKB = "ckb"
        const val LANGUAGE_AR = "ar"
        const val LANGUAGE_EN = "en"
        const val LANGUAGE_KMR = "kmr"
    }

    private val sp: SharedPreferences = appContext.getSharedPreferences(SP_SS_APP, Context.MODE_PRIVATE)
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

    fun saveSPInt(keySP: String, value: Int) {
        spEditor.putInt(keySP, value)
        spEditor.commit()
    }

    fun getSpInt(key: String, default: Int = 0): Int = sp.getInt(key, default)

    fun saveSPLong(keySP: String, value: Long) {
        spEditor.putLong(keySP, value)
        spEditor.commit()
    }

    fun getSpLong(key: String, default: Long = 0L): Long = sp.getLong(key, default)

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

    fun recordSuccessfulSync() {
        saveSPLong(SP_LAST_SYNC_SUCCESS_AT, System.currentTimeMillis())
    }

    /**
     * True after the user completed "Start" / sync at least once and playlist config still exists.
     * Used to open [DashboardActivity] directly from splash instead of [MainActivity] every launch.
     */
    fun shouldSkipActivationScreen(): Boolean {
        if (getSpLong(SP_LAST_SYNC_SUCCESS_AT, 0L) <= 0L) return false
        val m3u = getSpM3uDirect().trim()
        if (m3u.isNotEmpty()) return true
        if (ManagedPlaylistCache.hasCachedItems(appContext)) return true
        val pl = getSpPlaylist().trim()
        return pl.isNotEmpty() && pl != "[]"
    }

    fun getThemeMode(): String =
        sp.getString(SP_THEME_MODE, "dark") ?: "dark"

    fun saveThemeMode(mode: String) {
        saveSPString(SP_THEME_MODE, mode)
    }

    /** Stored key: ckb, ar, en, kmr — default ckb (Kurdish Sorani). Legacy `system` is persisted as ckb. */
    fun getAppLanguageKey(): String {
        val raw = sp.getString(SP_APP_LANGUAGE, LANGUAGE_CKB)?.trim().orEmpty()
        val key = when (raw) {
            "", LANGUAGE_SYSTEM -> LANGUAGE_CKB
            LANGUAGE_CKB, LANGUAGE_AR, LANGUAGE_EN, LANGUAGE_KMR -> raw
            else -> LANGUAGE_CKB
        }
        if (raw != key) {
            saveSPString(SP_APP_LANGUAGE, key)
        }
        return key
    }

    fun saveAppLanguageKey(key: String) {
        val normalized = when (key.trim()) {
            LANGUAGE_SYSTEM, "" -> LANGUAGE_CKB
            LANGUAGE_CKB, LANGUAGE_AR, LANGUAGE_EN, LANGUAGE_KMR -> key.trim()
            else -> LANGUAGE_CKB
        }
        saveSPString(SP_APP_LANGUAGE, normalized)
    }
}
package com.bachors.iptv.utils

import android.content.Context
import android.content.Intent
import com.bachors.iptv.IjkPlayerActivity
import com.bachors.iptv.PlayerActivity
import com.bachors.iptv.VlcPlayerActivity
import com.bachors.iptv.WebHlsPlayerActivity

/**
 * Starts the playback activity chosen in settings ([SharedPrefManager.SP_PLAYER_ENGINE]).
 */
object PlayerLauncher {

    /** Media3 ExoPlayer — balanced (default). */
    const val ENGINE_EXO = "exo"

    /** Same ExoPlayer core, large buffers + long reads — stable for weak networks / VOD. */
    const val ENGINE_EXO_CINEMA = "exo_cinema"

    /** Same ExoPlayer core, minimal live latency (more rebuffers on bad links). */
    const val ENGINE_EXO_ARENA = "exo_arena"

    /** Chromium WebView + hls.js — HLS (.m3u8) only; different pipeline from Exo. */
    const val ENGINE_WEB_HLS = "web_hls"

    /** LibVLC (LGPL). */
    const val ENGINE_VLC = "vlc"

    /** Bilibili IJK / FFmpeg (license depends on native build). */
    const val ENGINE_IJK = "ijk"

    fun start(context: Context, intent: Intent) {
        val engine = SharedPrefManager(context).getSpString(SharedPrefManager.SP_PLAYER_ENGINE)
            .trim()
            .lowercase()
            .ifEmpty { ENGINE_EXO }
        val target = when (engine) {
            ENGINE_IJK -> IjkPlayerActivity::class.java
            ENGINE_VLC -> VlcPlayerActivity::class.java
            ENGINE_WEB_HLS -> WebHlsPlayerActivity::class.java
            ENGINE_EXO, ENGINE_EXO_CINEMA, ENGINE_EXO_ARENA -> PlayerActivity::class.java
            else -> PlayerActivity::class.java
        }
        intent.setClass(context, target)
        context.startActivity(intent)
    }
}

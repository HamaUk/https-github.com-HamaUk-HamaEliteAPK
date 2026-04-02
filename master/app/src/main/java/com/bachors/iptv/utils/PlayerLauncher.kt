package com.bachors.iptv.utils

import android.content.Context
import android.content.Intent
import com.bachors.iptv.PlayerActivity

/**
 * Starts the playback activity chosen in settings ([SharedPrefManager.SP_PLAYER_ENGINE]).
 * Only ExoPlayer profiles remain: Standard, Cinema, Arena.
 */
object PlayerLauncher {

    /** Media3 ExoPlayer — balanced (default). */
    const val ENGINE_EXO = "exo"

    /** Same ExoPlayer core, large buffers + long reads — stable for weak networks / VOD. */
    const val ENGINE_EXO_CINEMA = "exo_cinema"

    /** Same ExoPlayer core, minimal live latency (more rebuffers on bad links). */
    const val ENGINE_EXO_ARENA = "exo_arena"

    fun start(context: Context, intent: Intent) {
        intent.setClass(context, PlayerActivity::class.java)
        context.startActivity(intent)
    }
}

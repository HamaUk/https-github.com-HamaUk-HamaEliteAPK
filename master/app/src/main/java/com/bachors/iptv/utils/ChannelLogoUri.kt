package com.bachors.iptv.utils

import android.net.Uri
import android.util.Log

/** Normalizes admin / M3U logo strings into a [Uri] Picasso can load (http(s), data:image, //). */
object ChannelLogoUri {
    private const val TAG = "ChannelLogo"

    fun parse(raw: String): Uri? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.startsWith("data:image/", ignoreCase = true) ->
                try {
                    Uri.parse(trimmed)
                } catch (_: Exception) {
                    null
                }
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ->
                try {
                    Uri.parse(trimmed)
                } catch (_: Exception) {
                    null
                }
            trimmed.startsWith("//") ->
                try {
                    Uri.parse("https:$trimmed")
                } catch (_: Exception) {
                    null
                }
            else -> null
        }
    }

    fun logLoadFailure(raw: String, e: Exception?) {
        Log.w(TAG, "logo load failed: ${raw.trim().take(96)}", e)
    }
}

package com.bachors.iptv.utils

import java.util.Locale

object M3uTypeDetect {
    fun detectTypeFromUrl(url: String): String {
        val lower = url.lowercase(Locale.US)
        return when {
            lower.contains("/movie/") || lower.contains("/movies/") -> "vod"
            lower.contains("/series/") || lower.contains("/episode/") -> "series"
            lower.contains("/live/") || lower.contains("/stream/") -> "live"
            lower.contains("output=mpegts") || lower.contains("output=ts") || lower.contains("type=m3u_plus") -> "live"
            lower.endsWith(".mp4") || lower.endsWith(".mkv") ||
                lower.endsWith(".avi") || lower.endsWith(".mov") ||
                lower.endsWith(".wmv") || lower.endsWith(".flv") ||
                lower.endsWith(".ts") || lower.endsWith(".m4v") -> "vod"
            lower.endsWith(".m3u8") -> "live"
            else -> "live"
        }
    }
}

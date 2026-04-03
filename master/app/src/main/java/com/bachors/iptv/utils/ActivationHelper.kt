package com.bachors.iptv.utils

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.util.Locale
import kotlin.math.abs

object ActivationHelper {

    /**
     * Generates a stable, unique 8-digit numeric code from the device's Android ID.
     */
    @SuppressLint("HardwareIds")
    fun getDeviceCode(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "00000000"
        
        // Use hashCode to get a semi-unique integer, then format to 8 digits
        // We use absolute value to ensure it's positive.
        // If it's more than 8 digits, we take modulo 100,000,000.
        val hash = abs(androidId.hashCode().toLong())
        val numericCode = hash % 100000000
        
        return String.format(Locale.US, "%08d", numericCode)
    }

    /**
     * Helper to format a timestamp into a readable date string.
     */
    fun formatExpiryDate(timestamp: Long): String {
        if (timestamp <= 0) return "Unlimited"
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

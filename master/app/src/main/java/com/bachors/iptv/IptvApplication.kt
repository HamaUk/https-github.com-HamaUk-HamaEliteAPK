package com.bachors.iptv

import android.app.Application
import android.content.Context
import com.bachors.iptv.utils.AppLocaleHelper
import com.bachors.iptv.utils.CrashReportHelper
import com.bachors.iptv.utils.ThemeHelper

class IptvApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // After super: applicationContext / prefs are safe on all OEMs; locale still applies before first Activity.
        AppLocaleHelper.applySavedApplicationLocales(this)
    }

    override fun onCreate() {
        super.onCreate()
        AppLocaleHelper.applySavedApplicationLocales(this)
        try {
            CrashReportHelper.install(this)
        } catch (e: Exception) {
            android.util.Log.e("IptvApplication", "CrashReportHelper.install", e)
        }
        ThemeHelper.applyDefaultNightMode(this)
    }
}

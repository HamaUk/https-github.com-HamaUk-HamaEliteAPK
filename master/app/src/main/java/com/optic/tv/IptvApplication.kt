package com.optic.tv

import android.app.Application
import android.content.Context
import com.optic.tv.utils.AppLocaleHelper
import com.optic.tv.utils.CrashReportHelper
import com.optic.tv.utils.ThemeHelper

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

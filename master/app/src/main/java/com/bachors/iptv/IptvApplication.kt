package com.bachors.iptv

import android.app.Application
import android.content.Context
import com.bachors.iptv.utils.AppLocaleHelper
import com.bachors.iptv.utils.CrashReportHelper
import com.bachors.iptv.utils.ThemeHelper

class IptvApplication : Application() {
    override fun attachBaseContext(base: Context) {
        // Apply before super so the first configuration (splash, etc.) resolves Sorani resources.
        AppLocaleHelper.applySavedApplicationLocales(base)
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        AppLocaleHelper.applySavedApplicationLocales(this)
        super.onCreate()
        CrashReportHelper.install(this)
        ThemeHelper.applyDefaultNightMode(this)
    }
}

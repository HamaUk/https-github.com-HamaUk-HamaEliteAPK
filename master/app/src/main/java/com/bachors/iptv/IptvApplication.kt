package com.bachors.iptv

import android.app.Application
import android.content.Context
import com.bachors.iptv.utils.AppLocaleHelper
import com.bachors.iptv.utils.ThemeHelper

class IptvApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        AppLocaleHelper.applySavedApplicationLocales(base)
    }

    override fun onCreate() {
        super.onCreate()
        ThemeHelper.applyDefaultNightMode(this)
        AppLocaleHelper.applySavedApplicationLocales(this)
    }
}

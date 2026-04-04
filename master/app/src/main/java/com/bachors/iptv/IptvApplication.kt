package com.bachors.iptv

import android.app.Application
import com.bachors.iptv.utils.AppLocaleHelper
import com.bachors.iptv.utils.ThemeHelper

class IptvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeHelper.applyDefaultNightMode(this)
        AppLocaleHelper.applySavedApplicationLocales(this)
    }
}

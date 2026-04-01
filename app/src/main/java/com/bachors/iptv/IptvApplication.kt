package com.bachors.iptv

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class IptvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ckb-IQ"))
    }
}

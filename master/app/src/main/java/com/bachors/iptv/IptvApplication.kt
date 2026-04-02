package com.bachors.iptv

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class IptvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("spIPTV", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("spLanguage", "ckb") ?: "ckb"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLang))
    }
}

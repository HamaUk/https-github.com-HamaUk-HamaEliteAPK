package com.bachors.iptv.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.bachors.iptv.R

object ThemeHelper {
    const val THEME_DARK = "dark"
    const val THEME_AMOLED = "amoled"
    const val THEME_LIGHT = "light"

    fun getFullscreenThemeResId(context: Context): Int {
        return when (SharedPrefManager(context).getThemeMode()) {
            THEME_LIGHT -> R.style.AppTheme_FullScreen_Light
            THEME_AMOLED -> R.style.AppTheme_FullScreen_Amoled
            else -> R.style.AppTheme_FullScreen
        }
    }

    /** Call from [android.app.Application.onCreate] so night mode matches saved theme. */
    fun applyDefaultNightMode(context: Context) {
        val mode = SharedPrefManager(context).getThemeMode()
        AppCompatDelegate.setDefaultNightMode(
            if (mode == THEME_LIGHT) AppCompatDelegate.MODE_NIGHT_NO
            else AppCompatDelegate.MODE_NIGHT_YES
        )
    }

    /** Material alert dialogs: light surface when app theme is light. */
    fun getMaterialAlertDialogThemeResId(context: Context): Int {
        return when (SharedPrefManager(context).getThemeMode()) {
            THEME_LIGHT -> R.style.MyDialogThemeLight
            else -> R.style.MyDialogTheme
        }
    }
}

object AppLocaleHelper {
    fun applySavedApplicationLocales(context: Context) {
        val key = SharedPrefManager(context).getAppLanguageKey()
        val tags = when (key) {
            SharedPrefManager.LANGUAGE_SYSTEM -> null
            SharedPrefManager.LANGUAGE_CKB -> "ckb-IQ"
            SharedPrefManager.LANGUAGE_AR -> "ar"
            SharedPrefManager.LANGUAGE_EN -> "en"
            SharedPrefManager.LANGUAGE_KMR -> "kmr"
            else -> "ckb-IQ"
        }
        if (tags == null) {
            AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(tags))
        }
    }
}

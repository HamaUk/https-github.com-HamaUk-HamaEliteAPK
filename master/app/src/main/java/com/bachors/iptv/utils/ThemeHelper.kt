package com.bachors.iptv.utils

import android.content.Context
import android.graphics.Color
import android.view.View
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

    /** Dashboard / Settings use [R.drawable.premium_page_bg] in XML; re-apply when theme changes. */
    fun applyPremiumHeroBackground(view: View) {
        when (SharedPrefManager(view.context).getThemeMode()) {
            THEME_LIGHT -> view.setBackgroundResource(R.drawable.premium_page_bg_light)
            THEME_AMOLED -> view.setBackgroundColor(Color.BLACK)
            else -> view.setBackgroundResource(R.drawable.premium_page_bg)
        }
    }

    /** Main activation screen uses a flat color in XML. */
    fun applyMainActivationBackground(view: View) {
        when (SharedPrefManager(view.context).getThemeMode()) {
            THEME_LIGHT -> view.setBackgroundColor(Color.parseColor("#EEF1F6"))
            THEME_AMOLED -> view.setBackgroundColor(Color.BLACK)
            else -> view.setBackgroundColor(Color.parseColor("#050505"))
        }
    }
}

object AppLocaleHelper {
    /**
     * Applies a concrete app locale (never "follow system").
     * Default and fallback: Kurdish Sorani (`ckb`) to match `values/` + `values-ckb` and [locales_config].
     */
    fun applySavedApplicationLocales(context: Context) {
        val key = SharedPrefManager(context).getAppLanguageKey()
        val tags = when (key) {
            SharedPrefManager.LANGUAGE_AR -> "ar"
            SharedPrefManager.LANGUAGE_EN -> "en"
            SharedPrefManager.LANGUAGE_KMR -> "kmr"
            else -> "ckb"
        }
        AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(tags))
    }
}

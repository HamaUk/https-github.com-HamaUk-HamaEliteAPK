package com.optic.tv

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.optic.tv.utils.AppLocaleHelper
import com.optic.tv.utils.ThemeHelper
import java.util.Locale

/**
 * Applies saved locale and fullscreen theme before [super.onCreate].
 * Layout is always LTR for every language (Arabic, Sorani, etc.); only strings change direction naturally in text views.
 */
abstract class BaseThemedAppCompatActivity : AppCompatActivity() {

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration) {
        val config = Configuration(overrideConfiguration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(Locale.ENGLISH)
        }
        super.applyOverrideConfiguration(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLocaleHelper.applySavedApplicationLocales(this)
        ThemeHelper.applyDefaultNightMode(this)
        setTheme(ThemeHelper.getFullscreenThemeResId(this))
        super.onCreate(savedInstanceState)
    }
}

package com.bachors.iptv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bachors.iptv.utils.ThemeHelper

/**
 * Applies saved fullscreen theme (dark / AMOLED / light) before [super.onCreate].
 */
abstract class BaseThemedAppCompatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.getFullscreenThemeResId(this))
        super.onCreate(savedInstanceState)
    }
}

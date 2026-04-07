package com.optic.tv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.optic.tv.utils.CrashReportHelper
import com.optic.tv.utils.SharedPrefManager
import com.optic.tv.utils.ThemeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SplashActivity : BaseThemedAppCompatActivity() {

    private val splashDelay = 2000L // 2 seconds
    private val handler = Handler(Looper.getMainLooper())
    private var isCancelled = false

    private val navigateAfterSplashRunnable = Runnable {
        if (!isCancelled) {
            val prefs = SharedPrefManager(this)
            val next = if (prefs.shouldSkipActivationScreen()) {
                Intent(this, DashboardActivity::class.java)
            } else {
                Intent(this, MainActivity::class.java)
            }
            startActivity(next)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullscreen()
        setContentView(R.layout.activity_splash)
        (findViewById<ViewGroup>(android.R.id.content).getChildAt(0))?.let {
            ThemeHelper.applyPremiumHeroBackground(it)
        }

        val logoContainer = findViewById<LinearLayout>(R.id.logo_container)
        val subtitleText = findViewById<TextView>(R.id.subtitle_text)

        if (logoContainer != null && subtitleText != null) {
            logoContainer.alpha = 0f
            logoContainer.scaleX = 0.8f
            logoContainer.scaleY = 0.8f
            subtitleText.alpha = 0f
            logoContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .withEndAction {
                    subtitleText.animate()
                        .alpha(1f)
                        .setDuration(400)
                        .start()
                }
                .start()
        }

        scheduleAfterSplashOrCrashDialog()
    }

    private fun scheduleAfterSplashOrCrashDialog() {
        try {
            if (CrashReportHelper.hasPendingReport(this)) {
                CrashReportHelper.readPendingReport(this)
                MaterialAlertDialogBuilder(this, ThemeHelper.getMaterialAlertDialogThemeResId(this))
                    .setTitle(R.string.crash_report_title)
                    .setMessage(R.string.crash_report_message)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        CrashReportHelper.clearReport(this)
                        handler.postDelayed(navigateAfterSplashRunnable, 300)
                    }
                    .show()
                return
            }
        } catch (e: Exception) {
            Log.e("SplashActivity", "crash dialog", e)
            try {
                CrashReportHelper.clearReport(this)
            } catch (_: Exception) {
            }
            Toast.makeText(this, R.string.crash_report_message, Toast.LENGTH_LONG).show()
        }
        handler.postDelayed(navigateAfterSplashRunnable, splashDelay)
    }

    override fun onDestroy() {
        isCancelled = true
        handler.removeCallbacks(navigateAfterSplashRunnable)
        super.onDestroy()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        isCancelled = true
        handler.removeCallbacks(navigateAfterSplashRunnable)
    }

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.hide(WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        supportActionBar?.hide()
    }
}

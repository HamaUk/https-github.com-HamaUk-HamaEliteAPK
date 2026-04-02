package com.bachors.iptv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class SplashActivity : AppCompatActivity() {

    private val splashDelay = 2000L // 2 seconds
    private val handler = Handler(Looper.getMainLooper())
    private var isCancelled = false

    private val navigateToMainRunnable = Runnable {
        if (!isCancelled) {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullscreen()
        setContentView(R.layout.activity_splash)

        val logoContainer = findViewById<LinearLayout>(R.id.logo_container)
        val subtitleText = findViewById<TextView>(R.id.subtitle_text)

        // Simple scale and alpha animation for the logo
        logoContainer.alpha = 0f
        logoContainer.scaleX = 0.8f
        logoContainer.scaleY = 0.8f
        
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

        subtitleText.alpha = 0f

        // Schedule navigation to MainActivity
        handler.postDelayed(navigateToMainRunnable, splashDelay)
    }

    override fun onDestroy() {
        isCancelled = true
        handler.removeCallbacks(navigateToMainRunnable)
        super.onDestroy()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        isCancelled = true
        handler.removeCallbacks(navigateToMainRunnable)
    }

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.hide(WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        supportActionBar?.hide()
    }
}

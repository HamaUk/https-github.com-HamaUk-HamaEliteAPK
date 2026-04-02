package com.bachors.iptv

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bachors.iptv.models.ChannelsData
import com.bachors.iptv.utils.SharedPrefManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * HLS-only pipeline: Chromium + [hls.js] (different demux/decode path than ExoPlayer).
 * Best for `.m3u8` / live playlists; use Exo or LibVLC for raw TS/MPEG-TS-only URLs.
 */
class WebHlsPlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loading: ProgressBar
    private lateinit var tvTitle: TextView

    private var channelList = mutableListOf<ChannelsData>()
    private var currentIndex = 0
    private var currentUrl: String = ""
    private var currentName: String = ""
    private var pageReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullscreen()
        setContentView(R.layout.activity_web_hls_player)

        val url = intent.getStringExtra("url")?.trim().orEmpty()
        if (url.isEmpty()) {
            Toast.makeText(this, "بەستەری پەخش نییە", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (!isLikelyHlsUrl(url)) {
            Toast.makeText(
                this,
                "بزوێنەری Web HLS تەنها بۆ .m3u8 / HLS. بزوێنەرێکی تر هەڵبژێرە.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        currentUrl = url
        currentName = intent.getStringExtra("name") ?: "کەناڵ"
        loadChannelList(url)

        webView = findViewById(R.id.web_hls_view)
        loading = findViewById(R.id.web_hls_loading)
        tvTitle = findViewById(R.id.web_hls_title)
        tvTitle.text = currentName.uppercase()

        findViewById<ImageView>(R.id.web_hls_back).setOnClickListener { finish() }

        setupWebView()
        webView.loadUrl("file:///android_asset/hls_bridge.html")
    }

    private fun isLikelyHlsUrl(url: String): Boolean {
        val u = url.lowercase()
        return u.contains(".m3u8") || u.contains("application/vnd.apple.mpegurl") || u.contains("/hls/")
    }

    private fun loadChannelList(openUrl: String) {
        try {
            val json = SharedPrefManager(this).getSpChannels()
            if (json.isNotEmpty() && json != "[]") {
                val type = object : TypeToken<List<ChannelsData>>() {}.type
                channelList = Gson().fromJson<List<ChannelsData>>(json, type).toMutableList()
                currentIndex = channelList.indexOfFirst { it.url.trim() == openUrl.trim() }.coerceAtLeast(0)
            }
        } catch (_: Exception) { }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean = false

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageReady = true
                injectPlay(currentUrl)
                loading.visibility = View.GONE
            }
        }
        webView.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            domStorageEnabled = true
        }
    }

    private fun injectPlay(url: String) {
        val b64 = Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        webView.evaluateJavascript("if(window.playFromB64)playFromB64('$b64');", null)
        SharedPrefManager(this).saveSPString(SharedPrefManager.SP_CURRENT_URL, url)
    }

    private fun switchChannel(url: String, name: String) {
        if (!isLikelyHlsUrl(url)) {
            Toast.makeText(this, "ئەم کەناڵە HLS نییە — بگۆڕدرێت بۆ Exo/LibVLC.", Toast.LENGTH_SHORT).show()
            return
        }
        currentUrl = url
        currentName = name
        tvTitle.text = name.uppercase()
        if (pageReady) {
            injectPlay(url)
        } else {
            loading.visibility = View.VISIBLE
            webView.reload()
        }
    }

    private fun nextChannel() {
        if (channelList.isEmpty()) return
        currentIndex = (currentIndex + 1) % channelList.size
        val ch = channelList[currentIndex]
        switchChannel(ch.url.trim(), ch.name)
    }

    private fun prevChannel() {
        if (channelList.isEmpty()) return
        currentIndex = (currentIndex - 1 + channelList.size) % channelList.size
        val ch = channelList[currentIndex]
        switchChannel(ch.url.trim(), ch.name)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                nextChannel()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                prevChannel()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.destroy()
        } catch (_: Exception) { }
        super.onDestroy()
    }

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.hide(WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

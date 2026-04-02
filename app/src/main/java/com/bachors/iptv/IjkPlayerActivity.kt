package com.bachors.iptv

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
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
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

/**
 * Bilibili IJKPlayer / FFmpeg pipeline (LGPL/GPL — depends on your FFmpeg build).
 * Native `.so` come from `tv.danmaku.android/ijkplayer:*` AARs; fewer UI features than [PlayerActivity].
 */
class IjkPlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private var ijk: IjkMediaPlayer? = null
    private lateinit var surfaceView: SurfaceView
    private lateinit var loading: ProgressBar
    private lateinit var tvTitle: TextView

    private var channelList = mutableListOf<ChannelsData>()
    private var currentIndex = 0
    private var currentUrl: String = ""
    private var currentName: String = ""
    private var intentUserAgent: String = ""
    private var intentReferrer: String = ""
    private var surfaceReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullscreen()
        setContentView(R.layout.activity_ijk_player)

        val url = intent.getStringExtra("url")?.trim().orEmpty()
        if (url.isEmpty()) {
            Toast.makeText(this, "بەستەری پەخش نییە", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            IjkMediaPlayer.loadLibrariesOnce(null)
        } catch (e: UnsatisfiedLinkError) {
            Toast.makeText(
                this,
                "IJK native libs نەدۆزرانەوە. Gradle sync بکە (tv.danmaku.android AARs).",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        intentUserAgent = intent.getStringExtra("userAgent").orEmpty().trim()
        intentReferrer = intent.getStringExtra("referrer").orEmpty().trim()
        currentUrl = url
        currentName = intent.getStringExtra("name") ?: "کەناڵ"
        loadChannelList(url)

        surfaceView = findViewById(R.id/ijk_surface)
        loading = findViewById(R.id/ijk_loading)
        tvTitle = findViewById(R.id/ijk_title)
        tvTitle.text = currentName.uppercase()
        findViewById<ImageView>(R.id/ijk_btn_back).setOnClickListener { finish() }

        surfaceView.holder.addCallback(this)
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

    private fun buildHeaders(): Map<String, String> {
        val h = HashMap<String, String>()
        if (intentUserAgent.isNotEmpty()) h["User-Agent"] = intentUserAgent
        if (intentReferrer.isNotEmpty()) h["Referer"] = intentReferrer
        return h
    }

    private fun applyIjkOptions(mp: IjkMediaPlayer) {
        val hw = SharedPrefManager(this).getHwAccel()
        if (hw) {
            mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
            mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
        } else {
            mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0)
        }
        mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0)
        mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1)
        mp.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0)
        mp.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0)
        mp.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 20000000)
        mp.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1)
    }

    private fun attachListeners(mp: IjkMediaPlayer) {
        mp.setOnPreparedListener(IMediaPlayer.OnPreparedListener {
            loading.visibility = View.GONE
            it.start()
        })
        mp.setOnErrorListener(IMediaPlayer.OnErrorListener { _, _, _ ->
            runOnUiThread {
                loading.visibility = View.GONE
                Toast.makeText(this@IjkPlayerActivity, "هەڵەی IJK پەخش", Toast.LENGTH_SHORT).show()
            }
            true
        })
        mp.setOnInfoListener(IMediaPlayer.OnInfoListener { _, what, _ ->
            runOnUiThread {
                when (what) {
                    IMediaPlayer.MEDIA_INFO_BUFFERING_START -> loading.visibility = View.VISIBLE
                    IMediaPlayer.MEDIA_INFO_BUFFERING_END -> loading.visibility = View.GONE
                }
            }
            true
        })
    }

    private fun ensurePlayer(): IjkMediaPlayer {
        var p = ijk
        if (p == null) {
            p = IjkMediaPlayer().also { mp ->
                attachListeners(mp)
                applyIjkOptions(mp)
                ijk = mp
            }
        }
        return p
    }

    private fun openUrl(url: String) {
        if (!surfaceReady) return
        loading.visibility = View.VISIBLE
        val mp = ensurePlayer()
        try {
            mp.reset()
            applyIjkOptions(mp)
            mp.setDisplay(surfaceView.holder)
            mp.setDataSource(this, Uri.parse(url), buildHeaders())
            mp.prepareAsync()
            SharedPrefManager(this).saveSPString(SharedPrefManager.SP_CURRENT_URL, url)
        } catch (e: Exception) {
            loading.visibility = View.GONE
            Toast.makeText(this, e.message ?: "IJK", Toast.LENGTH_SHORT).show()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        openUrl(currentUrl)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        ijk?.setDisplay(null)
    }

    private fun switchChannel(url: String, name: String) {
        currentUrl = url
        currentName = name
        tvTitle.text = name.uppercase()
        if (surfaceReady) openUrl(url)
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
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                ijk?.let {
                    if (it.isPlaying) it.pause() else it.start()
                }
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

    override fun onPause() {
        try {
            ijk?.pause()
        } catch (_: Exception) { }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        try {
            ijk?.start()
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        try {
            surfaceView.holder.removeCallback(this)
        } catch (_: Exception) { }
        try {
            ijk?.setDisplay(null)
            ijk?.stop()
            ijk?.release()
        } catch (_: Exception) { }
        ijk = null
        super.onDestroy()
    }

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.hide(WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

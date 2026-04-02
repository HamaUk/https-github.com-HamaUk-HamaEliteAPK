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
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Alternate playback engine (LibVLC). Fewer features than [PlayerActivity] (no PiP, tracks UI, etc.)
 * but can help with odd streams. Choose in settings → پەخشکەر.
 */
class VlcPlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var surfaceView: SurfaceView
    private lateinit var loading: ProgressBar
    private lateinit var tvTitle: TextView

    private var channelList = mutableListOf<ChannelsData>()
    private var currentIndex = 0
    private var currentUrl: String = ""
    private var currentName: String = ""

    private var surfaceReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullscreen()
        setContentView(R.layout.activity_vlc_player)

        val url = intent.getStringExtra("url")?.trim().orEmpty()
        if (url.isEmpty()) {
            Toast.makeText(this, "بەستەری پەخش نییە", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentUrl = url
        currentName = intent.getStringExtra("name") ?: "کەناڵ"
        val intentUserAgent = intent.getStringExtra("userAgent").orEmpty().trim()
        val intentReferrer = intent.getStringExtra("referrer").orEmpty().trim()

        surfaceView = findViewById(R.id.vlc_surface)
        loading = findViewById(R.id.vlc_loading)
        tvTitle = findViewById(R.id.vlc_title)
        tvTitle.text = currentName.uppercase()

        findViewById<ImageView>(R.id.vlc_btn_back).setOnClickListener { finish() }

        loadChannelList(url)

        val options = ArrayList<String>().apply {
            add("--network-caching=2000")
            add("--aout=opensles")
            if (intentUserAgent.isNotEmpty()) add(":http-user-agent=$intentUserAgent")
            if (intentReferrer.isNotEmpty()) add(":http-referrer=$intentReferrer")
            if (!SharedPrefManager(this@VlcPlayerActivity).getHwAccel()) {
                add("--avcodec-hw=none")
            }
        }

        libVlc = LibVLC(this, options)
        mediaPlayer = MediaPlayer(libVlc).also { mp ->
            mp.setEventListener { ev ->
                runOnUiThread {
                    when (ev.type) {
                        MediaPlayer.Event.Buffering ->
                            loading.visibility = if (ev.buffering < 99.5f) View.VISIBLE else View.GONE
                        MediaPlayer.Event.Playing -> loading.visibility = View.GONE
                        MediaPlayer.Event.EncounteredError -> {
                            loading.visibility = View.GONE
                            Toast.makeText(this@VlcPlayerActivity, "هەڵەی پەخش", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

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

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        val mp = mediaPlayer ?: return
        val vlc = libVlc ?: return
        val vout = mp.vlcVout
        vout.setVideoSurface(holder.surface, holder)
        vout.attachViews()
        startPlaybackInternal(vlc, mp, currentUrl)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mediaPlayer?.vlcVout?.setWindowSize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        try {
            mediaPlayer?.vlcVout?.detachViews()
        } catch (_: Exception) { }
    }

    private fun startPlaybackInternal(vlc: LibVLC, mp: MediaPlayer, url: String) {
        loading.visibility = View.VISIBLE
        try {
            mp.stop()
        } catch (_: Exception) { }

        val media = Media(vlc, Uri.parse(url)).apply {
            if (SharedPrefManager(this@VlcPlayerActivity).getHwAccel()) {
                setHWDecoderEnabled(true, false)
            } else {
                setHWDecoderEnabled(false, true)
            }
            addOption(":network-caching=2000")
        }
        mp.media = media
        media.release()
        mp.play()
        SharedPrefManager(this).saveSPString(SharedPrefManager.SP_CURRENT_URL, url)
    }

    private fun switchChannel(url: String, name: String) {
        currentUrl = url
        currentName = name
        tvTitle.text = name.uppercase()
        val vlc = libVlc ?: return
        val mp = mediaPlayer ?: return
        if (surfaceReady) {
            startPlaybackInternal(vlc, mp, url)
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
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                mediaPlayer?.let {
                    if (it.isPlaying) it.pause() else it.play()
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

    override fun onDestroy() {
        try {
            surfaceView.holder.removeCallback(this)
        } catch (_: Exception) { }
        try {
            mediaPlayer?.vlcVout?.detachViews()
        } catch (_: Exception) { }
        mediaPlayer?.release()
        mediaPlayer = null
        libVlc?.release()
        libVlc = null
        super.onDestroy()
    }

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.hide(WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

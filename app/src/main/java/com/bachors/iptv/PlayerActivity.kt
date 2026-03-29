package com.bachors.iptv

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.bachors.iptv.models.ChannelsData
import com.bachors.iptv.utils.SharedPrefManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    // Player
    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var loadingBar: ProgressBar
    private lateinit var errorText: TextView

    // Controls
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var btnBack: ImageView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnPrev: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var btnFullscreen: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var seekSpacer: View
    private lateinit var tvChannel: TextView
    private lateinit var tvEpg: TextView
    private lateinit var tvClock: TextView

    // State
    private var channelList = mutableListOf<ChannelsData>()
    private var currentIndex = 0
    private var controlsVisible = true
    private var isFullscreen = false
    private var okHttpFactory: OkHttpDataSource.Factory? = null

    private val handler = Handler(Looper.getMainLooper())

    private val hideControlsRunnable = Runnable { hideControls() }

    private val clockRunnable = object : Runnable {
        override fun run() {
            val sdf = SimpleDateFormat("HH:mm  EEE dd MMM", Locale.getDefault())
            try { tvClock.text = sdf.format(Date()) } catch (_: Exception) {}
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullscreen()
        setContentView(R.layout.activity_player)

        bindViews()

        val name = intent.getStringExtra("name") ?: "Channel"
        val url  = intent.getStringExtra("url")  ?: ""

        if (url.isEmpty()) {
            Toast.makeText(this, "No stream URL", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        // Load channel list from SharedPrefs so Prev/Next work
        loadChannelList(url)

        setupControls()
        initPlayer()
        playUrl(url, name)
        handler.post(clockRunnable)
        scheduleHideControls()
    }

    // ─────────────────────────────────────────────────
    // VIEW BINDING
    // ─────────────────────────────────────────────────
    private fun bindViews() {
        playerView   = findViewById(R.id.player_view)
        loadingBar   = findViewById(R.id.player_loading)
        errorText    = findViewById(R.id.player_error)
        topBar       = findViewById(R.id.player_top_bar)
        bottomBar    = findViewById(R.id.player_bottom_bar)
        btnBack      = findViewById(R.id.btn_back)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnPrev      = findViewById(R.id.btn_prev_channel)
        btnNext      = findViewById(R.id.btn_next_channel)
        btnFullscreen= findViewById(R.id.btn_fullscreen)
        seekBar      = findViewById(R.id.seek_bar)
        seekSpacer   = findViewById(R.id.seek_spacer)
        tvChannel    = findViewById(R.id.tv_current_channel)
        tvEpg        = findViewById(R.id.tv_current_epg)
        tvClock      = findViewById(R.id.tv_clock)
    }

    // ─────────────────────────────────────────────────
    // CHANNEL LIST (for Prev / Next)
    // ─────────────────────────────────────────────────
    private fun loadChannelList(currentUrl: String) {
        try {
            val json = SharedPrefManager(this).getSpChannels()
            if (json.isNotEmpty() && json != "[]") {
                val type = object : TypeToken<List<ChannelsData>>() {}.type
                channelList = Gson().fromJson<List<ChannelsData>>(json, type).toMutableList()
                currentIndex = channelList.indexOfFirst { it.url == currentUrl }.coerceAtLeast(0)
            }
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────
    // CONTROLS SETUP
    // ─────────────────────────────────────────────────
    private fun setupControls() {
        // Tap anywhere on player to toggle controls
        playerView.setOnClickListener { toggleControls() }

        btnBack.setOnClickListener { finish() }

        btnPlayPause.setOnClickListener {
            exoPlayer?.let {
                if (it.isPlaying) it.pause() else it.play()
                updatePlayPauseIcon()
            }
            scheduleHideControls()
        }

        btnPrev.setOnClickListener {
            if (channelList.isNotEmpty()) {
                currentIndex = (currentIndex - 1 + channelList.size) % channelList.size
                val ch = channelList[currentIndex]
                playUrl(ch.url, ch.name)
            }
            scheduleHideControls()
        }

        btnNext.setOnClickListener {
            if (channelList.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % channelList.size
                val ch = channelList[currentIndex]
                playUrl(ch.url, ch.name)
            }
            scheduleHideControls()
        }

        btnFullscreen.setOnClickListener {
            toggleFullscreen()
            scheduleHideControls()
        }
    }

    // ─────────────────────────────────────────────────
    // PLAYER
    // ─────────────────────────────────────────────────
    private fun initPlayer() {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        okHttpFactory = OkHttpDataSource.Factory(okHttp)

        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            playerView.player = player
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            loadingBar.visibility = View.VISIBLE
                            errorText.visibility  = View.GONE
                        }
                        Player.STATE_READY -> {
                            loadingBar.visibility = View.GONE
                            errorText.visibility  = View.GONE
                            updatePlayPauseIcon()
                            // Show seek bar only for VOD (duration > 0)
                            val hasDuration = player.duration > 0
                            seekBar.visibility    = if (hasDuration) View.VISIBLE else View.GONE
                            seekSpacer.visibility = if (hasDuration) View.GONE   else View.VISIBLE
                        }
                        Player.STATE_IDLE, Player.STATE_ENDED -> {
                            loadingBar.visibility = View.GONE
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseIcon()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    loadingBar.visibility = View.GONE
                    errorText.visibility  = View.VISIBLE
                    errorText.text        = "Stream unavailable"
                }
            })
        }
    }

    private fun playUrl(url: String, name: String) {
        val factory = okHttpFactory ?: return
        try {
            val uri  = Uri.parse(url.trim())
            val item = MediaItem.fromUri(uri)
            val src  = if (url.contains(".m3u8", ignoreCase = true)) {
                HlsMediaSource.Factory(factory).createMediaSource(item)
            } else {
                ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this, factory))
                    .createMediaSource(item)
            }
            exoPlayer?.run {
                stop()
                setMediaSource(src)
                prepare()
                playWhenReady = true
            }
            tvChannel.text = name.uppercase()
            tvEpg.text     = "No Information"
            loadingBar.visibility = View.VISIBLE
            errorText.visibility  = View.GONE
        } catch (e: Exception) {
            errorText.text        = "Could not load stream"
            errorText.visibility  = View.VISIBLE
            loadingBar.visibility = View.GONE
        }
    }

    private fun updatePlayPauseIcon() {
        val playing = exoPlayer?.isPlaying == true
        btnPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause_white else R.drawable.ic_play_white
        )
    }

    // ─────────────────────────────────────────────────
    // CONTROLS VISIBILITY (auto-hide)
    // ─────────────────────────────────────────────────
    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        controlsVisible = true
        topBar.animate().alpha(1f).setDuration(200).start()
        bottomBar.animate().alpha(1f).setDuration(200).start()
        topBar.visibility    = View.VISIBLE
        bottomBar.visibility = View.VISIBLE
        scheduleHideControls()
    }

    private fun hideControls() {
        controlsVisible = false
        topBar.animate().alpha(0f).setDuration(300).withEndAction { topBar.visibility = View.INVISIBLE }.start()
        bottomBar.animate().alpha(0f).setDuration(300).withEndAction { bottomBar.visibility = View.INVISIBLE }.start()
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 4000L)
    }

    // ─────────────────────────────────────────────────
    // FULLSCREEN
    // ─────────────────────────────────────────────────
    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        requestedOrientation = if (isFullscreen)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        btnFullscreen.setImageResource(
            if (isFullscreen) R.drawable.ic_fullscreen_exit_white else R.drawable.ic_fullscreen_white
        )
    }

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.hide(WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        supportActionBar?.hide()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isFullscreen = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        btnFullscreen.setImageResource(
            if (isFullscreen) R.drawable.ic_fullscreen_exit_white else R.drawable.ic_fullscreen_white
        )
        goFullscreen()
    }

    // ─────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────
    override fun onPause()   { super.onPause();   exoPlayer?.pause() }
    override fun onResume()  { super.onResume();  goFullscreen(); exoPlayer?.play() }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.release()
        exoPlayer = null
    }
}
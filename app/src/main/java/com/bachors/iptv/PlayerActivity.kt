package com.bachors.iptv

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.ui.PlayerView
import com.bachors.iptv.adapters.ChannelsAdapter
import com.bachors.iptv.utils.SharedPrefManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var loadingBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var channelAdapter: ChannelsAdapter
    private val handler = Handler(Looper.getMainLooper())

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.hide(WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        supportActionBar?.hide()

        setContentView(R.layout.activity_player)

        playerView   = findViewById(R.id.player_view)
        loadingBar   = findViewById(R.id.player_loading)
        errorText    = findViewById(R.id.player_error)

        val name = intent.getStringExtra("name") ?: "Channel"
        val url  = intent.getStringExtra("url")  ?: ""

        if (url.isEmpty()) {
            Toast.makeText(this, "No stream URL provided", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupHeader(name)
        setupPlayer(url)
        startClock()
    }

    private fun setupHeader(name: String) {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_current_channel).text = name.uppercase()
        findViewById<TextView>(R.id.tv_current_epg).text = "No Information"
    }

    private fun setupPlayer(url: String) {
        // Build OkHttp client with sensible timeouts for IPTV streams
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttp)

        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            playerView.player = player

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            loadingBar.visibility = View.VISIBLE
                            errorText.visibility = View.GONE
                        }
                        Player.STATE_READY -> {
                            loadingBar.visibility = View.GONE
                            errorText.visibility = View.GONE
                        }
                        Player.STATE_ENDED -> {
                            loadingBar.visibility = View.GONE
                        }
                        Player.STATE_IDLE -> {
                            loadingBar.visibility = View.GONE
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    loadingBar.visibility = View.GONE
                    errorText.visibility = View.VISIBLE
                    errorText.text = "Stream unavailable\n${error.message}"
                }
            })

            playUrl(url, dataSourceFactory, player)
        }
    }

    private fun playUrl(
        url: String,
        dataSourceFactory: OkHttpDataSource.Factory,
        player: ExoPlayer
    ) {
        try {
            val uri = Uri.parse(url.trim())
            val mediaItem = MediaItem.fromUri(uri)

            // Use HLS source for .m3u8 streams, otherwise default
            val mediaSource = if (url.contains(".m3u8", ignoreCase = true)) {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            } else {
                androidx.media3.exoplayer.source.ProgressiveMediaSource
                    .Factory(DefaultDataSource.Factory(this, dataSourceFactory))
                    .createMediaSource(mediaItem)
            }

            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
            loadingBar.visibility = View.VISIBLE
            errorText.visibility = View.GONE

        } catch (e: Exception) {
            loadingBar.visibility = View.GONE
            errorText.visibility = View.VISIBLE
            errorText.text = "Could not load stream"
        }
    }

    /** Called from sidebar — swaps the current stream without recreating the activity */
    private fun switchChannel(url: String, name: String) {
        val player = exoPlayer ?: return
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val dataSourceFactory = OkHttpDataSource.Factory(okHttp)
        player.stop()
        playUrl(url, dataSourceFactory, player)
        findViewById<TextView>(R.id.tv_current_channel).text = name.uppercase()
    }

    private fun startClock() {
        handler.post(clockRunnable)
    }

    private fun updateClock() {
        val sdf = SimpleDateFormat("HH:mm a | MMM dd yyyy", Locale.getDefault())
        runOnUiThread {
            try { findViewById<TextView>(R.id.tv_clock)?.text = sdf.format(Date()) } catch (_: Exception) {}
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
        exoPlayer?.release()
        exoPlayer = null
    }
}
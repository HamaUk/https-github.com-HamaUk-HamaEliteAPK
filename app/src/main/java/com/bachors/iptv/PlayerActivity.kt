package com.bachors.iptv

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
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

    // ── Player ──────────────────────────────────────────────
    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var loadingBar: ProgressBar
    private lateinit var errorText: TextView
    private var okHttpFactory: OkHttpDataSource.Factory? = null

    // ── Controls ────────────────────────────────────────────
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

    // ── Volume OSD ──────────────────────────────────────────
    private lateinit var volumeOsd: View
    private lateinit var osdIcon: ImageView
    private lateinit var osdPercent: TextView
    private lateinit var osdBar: ProgressBar
    private lateinit var audioManager: AudioManager

    // ── State ───────────────────────────────────────────────
    private var channelList = mutableListOf<ChannelsData>()
    private var currentIndex = 0
    private var controlsVisible = true
    private var isFullscreen = false
    private var lastRequestedUrl: String = ""
    private var fallbackTriedForUrl: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    private val hideVolumeOsdRunnable = Runnable { fadeOutVolumeOsd() }

    private val clockRunnable = object : Runnable {
        override fun run() {
            val sdf = SimpleDateFormat("HH:mm  EEE dd MMM", Locale.getDefault())
            try { tvClock.text = sdf.format(Date()) } catch (_: Exception) {}
            handler.postDelayed(this, 60_000L)
        }
    }

    // ════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullscreen()
        setContentView(R.layout.activity_player)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        bindViews()

        val name = intent.getStringExtra("name") ?: "Channel"
        val url  = intent.getStringExtra("url")  ?: ""

        if (url.isEmpty()) {
            Toast.makeText(this, "No stream URL", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        loadChannelList(url)
        setupControls()
        initPlayer()
        playUrl(url, name)
        handler.post(clockRunnable)
        scheduleHideControls()
    }

    override fun onPause()   { super.onPause();  exoPlayer?.pause() }
    override fun onResume()  { super.onResume(); goFullscreen(); exoPlayer?.play() }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isFullscreen = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        updateFullscreenIcon()
        goFullscreen()
    }

    // ════════════════════════════════════════════════════════
    //  REMOTE CONTROL — all key intercept here
    // ════════════════════════════════════════════════════════
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Show controls on any key press so user can see what's happening
        showControls()

        return when (keyCode) {
            // ── Navigation (D-pad) ──────────────────────────
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlayPause(); true }

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_NEXT       -> { nextChannel(); true }

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS   -> { prevChannel(); true }

            KeyEvent.KEYCODE_MEDIA_PLAY       -> { exoPlayer?.play();  updatePlayPauseIcon(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE      -> { exoPlayer?.pause(); updatePlayPauseIcon(); true }
            KeyEvent.KEYCODE_MEDIA_STOP       -> { exoPlayer?.pause(); true }

            // ── Volume ──────────────────────────────────────
            KeyEvent.KEYCODE_VOLUME_UP   -> { adjustVolume(+1); true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { adjustVolume(-1); true }
            KeyEvent.KEYCODE_VOLUME_MUTE -> { toggleMute(); true }

            // ── Back ────────────────────────────────────────
            KeyEvent.KEYCODE_BACK -> { finish(); true }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    // Stop system from also changing volume (we handle it visually)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) onKeyDown(event.keyCode, event)
            return true  // consume the event so system doesn't show its own OSD
        }
        return super.dispatchKeyEvent(event)
    }

    // ════════════════════════════════════════════════════════
    //  VOLUME OSD
    // ════════════════════════════════════════════════════════
    private fun adjustVolume(direction: Int) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVol = (curVol + direction).coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        showVolumeOsd(newVol, maxVol)
    }

    private fun toggleMute() {
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (curVol > 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            showVolumeOsd(0, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        } else {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol / 2, 0)
            showVolumeOsd(maxVol / 2, maxVol)
        }
    }

    private fun showVolumeOsd(current: Int, max: Int) {
        val pct = if (max > 0) (current * 100 / max) else 0

        osdPercent.text = "$pct%"
        osdBar.max      = 100
        osdBar.progress = pct

        osdIcon.setImageResource(when {
            pct == 0   -> R.drawable.ic_volume_mute
            pct <= 40  -> R.drawable.ic_volume_low
            else       -> R.drawable.ic_volume_high
        })

        // Animate in
        volumeOsd.visibility = View.VISIBLE
        volumeOsd.animate().cancel()
        volumeOsd.animate().alpha(1f).setDuration(150).start()

        // Schedule hide
        handler.removeCallbacks(hideVolumeOsdRunnable)
        handler.postDelayed(hideVolumeOsdRunnable, 2500L)
    }

    private fun fadeOutVolumeOsd() {
        volumeOsd.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction { volumeOsd.visibility = View.INVISIBLE }
            .start()
    }

    // ════════════════════════════════════════════════════════
    //  CHANNEL NAVIGATION
    // ════════════════════════════════════════════════════════
    private fun nextChannel() {
        if (channelList.isEmpty()) return
        currentIndex = (currentIndex + 1) % channelList.size
        val ch = channelList[currentIndex]
        playUrl(ch.url, ch.name)
        scheduleHideControls()
    }

    private fun prevChannel() {
        if (channelList.isEmpty()) return
        currentIndex = (currentIndex - 1 + channelList.size) % channelList.size
        val ch = channelList[currentIndex]
        playUrl(ch.url, ch.name)
        scheduleHideControls()
    }

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

    // ════════════════════════════════════════════════════════
    //  PLAYER
    // ════════════════════════════════════════════════════════
    private fun initPlayer() {
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        okHttpFactory = OkHttpDataSource.Factory(okHttp)

        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            player.volume = 1f
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
                            if (shouldFallbackForMissingAudio(player)) {
                                retryWithAlternateLiveUrl()
                                return
                            }
                            updatePlayPauseIcon()
                            val hasDuration = player.duration > 0
                            seekBar.visibility    = if (hasDuration) View.VISIBLE else View.GONE
                            seekSpacer.visibility = if (hasDuration) View.GONE   else View.VISIBLE
                        }
                        else -> {
                            loadingBar.visibility = View.GONE
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayPauseIcon()

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (retryWithAlternateLiveUrl()) return
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
            val playbackUrl = url.trim()
            lastRequestedUrl = playbackUrl
            fallbackTriedForUrl = ""
            ensureAudibleVolume()
            SharedPrefManager(this).saveSPString(SharedPrefManager.SP_CURRENT_URL, playbackUrl)
            val uri  = Uri.parse(playbackUrl)
            val item = MediaItem.fromUri(uri)
            val src  = if (playbackUrl.contains(".m3u8", ignoreCase = true)) {
                HlsMediaSource.Factory(factory).createMediaSource(item)
            } else {
                ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this, factory))
                    .createMediaSource(item)
            }
            exoPlayer?.run { stop(); setMediaSource(src); prepare(); playWhenReady = true }
            tvChannel.text = name.uppercase()
            tvEpg.text     = "No Information"
            loadingBar.visibility = View.VISIBLE
            errorText.visibility  = View.GONE
        } catch (_: Exception) {
            errorText.text        = "Could not load stream"
            errorText.visibility  = View.VISIBLE
            loadingBar.visibility = View.GONE
        }
    }

    private fun shouldFallbackForMissingAudio(player: ExoPlayer): Boolean {
        if (!Regex("""/live/""", RegexOption.IGNORE_CASE).containsMatchIn(lastRequestedUrl)) return false
        if (fallbackTriedForUrl == lastRequestedUrl) return false
        val hasAudioGroup = player.currentTracks.groups.any {
            it.type == C.TRACK_TYPE_AUDIO && it.mediaTrackGroup.length > 0
        }
        return !hasAudioGroup
    }

    private fun retryWithAlternateLiveUrl(): Boolean {
        val current = lastRequestedUrl
        if (current.isBlank()) return false
        if (fallbackTriedForUrl == current) return false
        val alt = alternateLiveUrl(current) ?: return false
        fallbackTriedForUrl = current
        try {
            val factory = okHttpFactory ?: return false
            val uri = Uri.parse(alt)
            val item = MediaItem.fromUri(uri)
            val src = if (alt.contains(".m3u8", ignoreCase = true)) {
                HlsMediaSource.Factory(factory).createMediaSource(item)
            } else {
                ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this, factory))
                    .createMediaSource(item)
            }
            exoPlayer?.run { stop(); setMediaSource(src); prepare(); playWhenReady = true }
            SharedPrefManager(this).saveSPString(SharedPrefManager.SP_CURRENT_URL, alt)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun alternateLiveUrl(url: String): String? {
        return when {
            Regex("""/live/[^/]+/[^/]+/\d+\.ts(\?.*)?$""", RegexOption.IGNORE_CASE).containsMatchIn(url) ->
                url.replace(Regex("""\.ts(\?.*)?$""", RegexOption.IGNORE_CASE), ".m3u8$1")
            Regex("""/live/[^/]+/[^/]+/\d+\.m3u8(\?.*)?$""", RegexOption.IGNORE_CASE).containsMatchIn(url) ->
                url.replace(Regex("""\.m3u8(\?.*)?$""", RegexOption.IGNORE_CASE), ".ts$1")
            else -> null
        }
    }

    private fun ensureAudibleVolume() {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (maxVol > 0 && curVol <= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.35f).toInt().coerceAtLeast(1), 0)
        }
    }

    // ════════════════════════════════════════════════════════
    //  CONTROLS UI
    // ════════════════════════════════════════════════════════
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
        // Volume OSD
        volumeOsd    = findViewById(R.id.volume_osd)
        osdIcon      = volumeOsd.findViewById(R.id.osd_volume_icon)
        osdPercent   = volumeOsd.findViewById(R.id.osd_volume_percent)
        osdBar       = volumeOsd.findViewById(R.id.osd_volume_bar)
    }

    private fun setupControls() {
        playerView.setOnClickListener { toggleControls() }
        btnBack.setOnClickListener { finish() }
        btnPlayPause.setOnClickListener { togglePlayPause(); scheduleHideControls() }
        btnPrev.setOnClickListener { prevChannel() }
        btnNext.setOnClickListener { nextChannel() }
        btnFullscreen.setOnClickListener { toggleFullscreen(); scheduleHideControls() }
    }

    private fun togglePlayPause() {
        exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
        updatePlayPauseIcon()
    }

    private fun updatePlayPauseIcon() {
        btnPlayPause.setImageResource(
            if (exoPlayer?.isPlaying == true) R.drawable.ic_pause_white else R.drawable.ic_play_white
        )
    }

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        controlsVisible      = true
        topBar.visibility    = View.VISIBLE
        bottomBar.visibility = View.VISIBLE
        topBar.animate().alpha(1f).setDuration(200).start()
        bottomBar.animate().alpha(1f).setDuration(200).start()
        scheduleHideControls()
    }

    private fun hideControls() {
        controlsVisible = false
        topBar.animate().alpha(0f).setDuration(300)
            .withEndAction { topBar.visibility = View.INVISIBLE }.start()
        bottomBar.animate().alpha(0f).setDuration(300)
            .withEndAction { bottomBar.visibility = View.INVISIBLE }.start()
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 4000L)
    }

    // ════════════════════════════════════════════════════════
    //  FULLSCREEN
    // ════════════════════════════════════════════════════════
    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        // Only change orientation on phones/tablets — TV devices reject requestedOrientation
        val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
        val isTelevision = uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        if (!isTelevision) {
            try {
                requestedOrientation = if (isFullscreen)
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR
            } catch (_: Exception) { /* ignore on restricted devices */ }
        }
        updateFullscreenIcon()
    }

    private fun updateFullscreenIcon() {
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
}
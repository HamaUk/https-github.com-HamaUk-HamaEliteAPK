package com.bachors.iptv

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.Tracks
import androidx.media3.common.util.Util
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bachors.iptv.models.ChannelsData
import com.bachors.iptv.utils.ContinueWatchingStore
import com.bachors.iptv.utils.PlayerLauncher
import com.bachors.iptv.utils.SharedPrefManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    private var okHttpClient: OkHttpClient? = null
    private lateinit var trackSelector: DefaultTrackSelector

    // ── Controls ────────────────────────────────────────────
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var btnBack: ImageView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnPrev: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var btnResize: ImageView
    private lateinit var btnAudioTrack: ImageView
    private lateinit var btnSubtitle: ImageView
    private lateinit var btnSpeed: TextView
    private lateinit var btnQuality: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var seekSpacer: View
    private lateinit var tvChannel: TextView
    private lateinit var tvEpg: TextView
    private lateinit var tvClock: TextView
    private lateinit var resizeOsd: TextView
    private lateinit var channelNumberOsd: TextView

    // ── Volume OSD ──────────────────────────────────────────
    private lateinit var volumeOsd: View
    private lateinit var osdIcon: ImageView
    private lateinit var osdPercent: TextView
    private lateinit var osdBar: ProgressBar
    private lateinit var audioManager: AudioManager

    // ── Resize modes ────────────────────────────────────────
    private val resizeModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    private val resizeLabels = arrayOf("گونجاندن لە شاشە", "پانی جێگیر", "زووم (100%)", "پڕکردنەوەی تەواو")
    private var currentResizeIndex = 0
    private val hideResizeOsdRunnable = Runnable { fadeOutResizeOsd() }

    // ── Playback Speed ──────────────────────────────────────
    private val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private val speedLabels = arrayOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x")
    private var currentSpeedIndex = 2

    // ── Channel Number Direct Input ─────────────────────────
    private var channelNumberBuffer = StringBuilder()
    private val commitChannelNumberRunnable = Runnable { commitChannelNumber() }
    private val hideChannelOsdRunnable = Runnable { fadeOutChannelOsd() }
    private val audioFallbackRunnable = Runnable { maybeRetryIfNoPlayableAudio() }

    // ── Auto-Reconnect ──────────────────────────────────────
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3
    private val maxLiveReconnectAttempts = 8
    private var reconnectPending = false
    private val reconnectRunnable = Runnable {
        reconnectPending = false
        attemptReconnect()
    }
    private val bufferingWatchdogRunnable = Runnable { onBufferingStallDetected() }

    // ── State ───────────────────────────────────────────────
    private var channelList = mutableListOf<ChannelsData>()
    private var currentIndex = 0
    private var controlsVisible = true
    private var isInPipMode = false
    private var lastRequestedUrl: String = ""
    private var fallbackTriedForUrl: String = ""
    private var sourceFallbackTriedForUrl: String = ""
    private var forceHlsForCurrentUrl: Boolean? = null
    private var currentChannelName: String = "کەناڵ"
    private var currentUserAgent: String = ""
    private var currentReferrer: String = ""
    private var intentUserAgent: String = ""
    private var intentReferrer: String = ""
    private var isLiveStream: Boolean = true
    private var contentTypeForPlayer: String = "live"
    private var shouldTrackContinueWatching: Boolean = false
    private var pendingResumeMs: Long = -1L
    private var seekbarUserDragging = false
    private var lastContinueWatchSaveAt = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    private val hideVolumeOsdRunnable = Runnable { fadeOutVolumeOsd() }

    companion object {
        private const val LIVE_MAX_VIDEO_BITRATE = 1_500_000
        private const val LIVE_BUFFERING_WATCHDOG_MS = 12_000L
    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            val sdf = SimpleDateFormat("HH:mm  EEE dd MMM", Locale.getDefault())
            try { tvClock.text = sdf.format(Date()) } catch (_: Exception) {}
            handler.postDelayed(this, 60_000L)
        }
    }

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            val p = exoPlayer
            if (p != null && !seekbarUserDragging && p.duration > 0) {
                seekBar.max = 1000
                seekBar.progress = ((p.currentPosition * 1000L) / p.duration).toInt().coerceIn(0, 1000)
            }
            if (shouldTrackContinueWatching) {
                val now = System.currentTimeMillis()
                if (now - lastContinueWatchSaveAt >= 10_000L) {
                    saveContinueWatchingProgress()
                    lastContinueWatchSaveAt = now
                }
            }
            handler.postDelayed(this, 500L)
        }
    }

    private fun videoCapsForPreset(preset: Int): Triple<Int, Int, Int> {
        val maxBr = when {
            !isLiveStream && preset == 0 -> Int.MAX_VALUE
            isLiveStream && preset == 0 -> LIVE_MAX_VIDEO_BITRATE
            preset == 1 -> 4_000_000
            preset == 2 -> 12_000_000
            else -> Int.MAX_VALUE
        }
        val (w, h) = when (preset) {
            1 -> 1280 to 720
            2 -> 1920 to 1080
            3 -> 3840 to 2160
            else -> Int.MAX_VALUE to Int.MAX_VALUE
        }
        return Triple(w, h, maxBr)
    }

    private fun applyVideoQualityPreset(preset: Int, showOsd: Boolean) {
        SharedPrefManager(this).saveSPInt(SharedPrefManager.SP_VIDEO_QUALITY_PRESET, preset)
        val (w, h, br) = videoCapsForPreset(preset)
        trackSelector.parameters = trackSelector.buildUponParameters()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setMaxVideoSize(w, h)
            .setMaxVideoBitrate(br)
            .build() as DefaultTrackSelector.Parameters
        if (showOsd) {
            val label = when (preset) {
                1 -> "720p"
                2 -> "1080p"
                3 -> "4K"
                else -> "خۆکار"
            }
            showGenericOsd("سنووری کوالێتی: $label")
        }
    }

    private fun saveContinueWatchingProgress() {
        if (!shouldTrackContinueWatching) return
        val p = exoPlayer ?: return
        if (lastRequestedUrl.isBlank()) return
        if (p.duration <= 0L) return
        ContinueWatchingStore.upsert(
            this,
            lastRequestedUrl,
            currentChannelName,
            p.currentPosition,
            p.duration,
            contentTypeForPlayer
        )
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

        val name = intent.getStringExtra("name") ?: "کەناڵ"
        val url  = intent.getStringExtra("url")  ?: ""
        intentUserAgent = intent.getStringExtra("userAgent").orEmpty().trim()
        intentReferrer = intent.getStringExtra("referrer").orEmpty().trim()
        isLiveStream = intent.getBooleanExtra("isLive", true)
        contentTypeForPlayer = intent.getStringExtra("contentType")?.trim()?.lowercase().orEmpty()
            .ifEmpty { if (isLiveStream) "live" else "vod" }
        shouldTrackContinueWatching =
            contentTypeForPlayer == "vod" || contentTypeForPlayer == "series"

        if (url.isEmpty()) {
            Toast.makeText(this, "بەستەری پەخش نییە", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        loadChannelList(url)
        setupControls()
        initPlayer()
        currentChannelName = name
        playUrl(url, name)
        handler.post(clockRunnable)
        handler.post(progressUpdateRunnable)
        scheduleHideControls()
    }

    override fun onPause() {
        saveContinueWatchingProgress()
        super.onPause()
        if (!isInPipMode) exoPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        goFullscreen()
        if (!isInPipMode) exoPlayer?.play()
    }

    override fun onDestroy() {
        saveContinueWatchingProgress()
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        goFullscreen()
    }

    // ── PiP ─────────────────────────────────────────────────
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfSupported()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            topBar.visibility = View.GONE
            bottomBar.visibility = View.GONE
            controlsVisible = false
        } else {
            showControls()
        }
    }

    private fun enterPipIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {}
        }
    }

    // ════════════════════════════════════════════════════════
    //  REMOTE CONTROL
    // ════════════════════════════════════════════════════════
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        showControls()

        // Number keys for direct channel input
        val digit = keyCodeToDigit(keyCode)
        if (digit >= 0) {
            onDigitPressed(digit)
            return true
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlayPause(); true }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_MEDIA_NEXT       -> { nextChannel(); true }

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS   -> { prevChannel(); true }

            // Only jump into the bottom bar when focus isn't already there — otherwise we steal
            // DPAD_RIGHT and reset focus to the first button, so audio / fullscreen stay unreachable.
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isFocusInsideBottomBar()) return super.onKeyDown(keyCode, event)
                focusBottomControls()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY       -> { exoPlayer?.play();  updatePlayPauseIcon(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE      -> { exoPlayer?.pause(); updatePlayPauseIcon(); true }
            KeyEvent.KEYCODE_MEDIA_STOP       -> { exoPlayer?.pause(); true }

            KeyEvent.KEYCODE_VOLUME_UP   -> { adjustVolume(+1); true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { adjustVolume(-1); true }
            KeyEvent.KEYCODE_VOLUME_MUTE -> { toggleMute(); true }

            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isFocusInsideBottomBar()) return super.onKeyDown(keyCode, event)
                finish()
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) onKeyDown(event.keyCode, event)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // ════════════════════════════════════════════════════════
    //  CHANNEL NUMBER DIRECT INPUT
    // ════════════════════════════════════════════════════════
    private fun keyCodeToDigit(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> 0
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
        KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
        KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
        else -> -1
    }

    private fun onDigitPressed(digit: Int) {
        handler.removeCallbacks(commitChannelNumberRunnable)
        channelNumberBuffer.append(digit)
        showChannelNumberOsd(channelNumberBuffer.toString())
        handler.postDelayed(commitChannelNumberRunnable, 1500L)
    }

    private fun commitChannelNumber() {
        val num = channelNumberBuffer.toString().toIntOrNull()
        channelNumberBuffer.clear()
        if (num == null || channelList.isEmpty()) {
            fadeOutChannelOsd()
            return
        }
        val targetIndex = (num - 1).coerceIn(0, channelList.size - 1)
        currentIndex = targetIndex
        val ch = channelList[currentIndex]
        playUrl(ch.url, ch.name)
        handler.postDelayed({ fadeOutChannelOsd() }, 500L)
        scheduleHideControls()
    }

    private fun showChannelNumberOsd(text: String) {
        channelNumberOsd.text = text
        channelNumberOsd.visibility = View.VISIBLE
        channelNumberOsd.animate().cancel()
        channelNumberOsd.animate().alpha(1f).setDuration(100).start()
        handler.removeCallbacks(hideChannelOsdRunnable)
    }

    private fun fadeOutChannelOsd() {
        channelNumberOsd.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction { channelNumberOsd.visibility = View.INVISIBLE }
            .start()
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
        volumeOsd.visibility = View.VISIBLE
        volumeOsd.animate().cancel()
        volumeOsd.animate().alpha(1f).setDuration(150).start()
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
    private fun activePlaybackEngine(): String {
        val e = SharedPrefManager(this).getSpString(SharedPrefManager.SP_PLAYER_ENGINE).trim().lowercase()
        return when (e) {
            PlayerLauncher.ENGINE_EXO_CINEMA -> PlayerLauncher.ENGINE_EXO_CINEMA
            PlayerLauncher.ENGINE_EXO_ARENA -> PlayerLauncher.ENGINE_EXO_ARENA
            PlayerLauncher.ENGINE_EXO -> PlayerLauncher.ENGINE_EXO
            else -> PlayerLauncher.ENGINE_EXO
        }
    }

    /**
     * Settings → hardware acceleration: when off, prefer software MediaCodec decoders (and FFmpeg ext)
     * to work around TV box HW decoder green/corrupt output.
     */
    private fun buildMediaCodecSelector(): MediaCodecSelector {
        if (SharedPrefManager(this).getHwAccel()) {
            return MediaCodecSelector.DEFAULT
        }
        return MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            try {
                val all = MediaCodecUtil.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
                val softwareOnly = all.filter { !it.hardwareAccelerated }
                if (softwareOnly.isNotEmpty()) softwareOnly else all
            } catch (_: MediaCodecUtil.DecoderQueryException) {
                MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
            }
        }
    }

    private fun initPlayer() {
        val savedResize = SharedPrefManager(this).getSpString(SharedPrefManager.SP_RESIZE_MODE)
        currentResizeIndex = resizeModes.indexOfFirst { it.toString() == savedResize }.coerceAtLeast(0)
        playerView.resizeMode = resizeModes[currentResizeIndex]

        val engine = activePlaybackEngine()
        val (connectSec, readSec) = when (engine) {
            PlayerLauncher.ENGINE_EXO_CINEMA -> 20L to 90L
            PlayerLauncher.ENGINE_EXO_ARENA -> 8L to 20L
            else -> 12L to 30L
        }
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(connectSec, TimeUnit.SECONDS)
            .readTimeout(readSec, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        okHttpClient = okHttp

        trackSelector = DefaultTrackSelector(this)
        val savedPreset = SharedPrefManager(this).getSpInt(SharedPrefManager.SP_VIDEO_QUALITY_PRESET, 0)
        val (capW, capH, capBr) = videoCapsForPreset(savedPreset)
        val trackParams = trackSelector.buildUponParameters()
            .setTunnelingEnabled(false)
            .setMaxAudioChannelCount(Int.MAX_VALUE)
            .setAudioOffloadPreferences(
                AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                    .build()
            )
            .setForceHighestSupportedBitrate(false)
            .setMaxVideoSize(capW, capH)
            .setMaxVideoBitrate(capBr)
            .build() as DefaultTrackSelector.Parameters
        trackSelector.parameters = trackParams

        // Buffer / latency profiles (settings → بزوێنەری پەخش): Standard, Cinema, Arena.
        val loadControlBuilder = DefaultLoadControl.Builder()
        if (isLiveStream) {
            when (engine) {
                PlayerLauncher.ENGINE_EXO_CINEMA ->
                    loadControlBuilder.setBufferDurationsMs(12_000, 60_000, 4000, 8000)
                PlayerLauncher.ENGINE_EXO_ARENA ->
                    loadControlBuilder.setBufferDurationsMs(800, 15_000, 250, 600)
                else ->
                    loadControlBuilder.setBufferDurationsMs(3000, 20_000, 1000, 2000)
            }
        } else {
            when (engine) {
                PlayerLauncher.ENGINE_EXO_CINEMA ->
                    loadControlBuilder.setBufferDurationsMs(8000, 120_000, 3000, 6000)
                PlayerLauncher.ENGINE_EXO_ARENA ->
                    loadControlBuilder.setBufferDurationsMs(1500, 20_000, 500, 1000)
                else ->
                    loadControlBuilder.setBufferDurationsMs(2500, 15_000, 800, 1500)
            }
        }
        val loadControl = loadControlBuilder.build()

        val hwAccel = SharedPrefManager(this).getHwAccel()
        val extMode = when {
            !hwAccel -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            engine == PlayerLauncher.ENGINE_EXO_CINEMA -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        }
        val renderersFactory = DefaultRenderersFactory(this)
            .setMediaCodecSelector(buildMediaCodecSelector())
            .setExtensionRendererMode(extMode)
            .setEnableDecoderFallback(true)
        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().also { player ->
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            player.volume = 1f
            player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            playerView.player = player
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            loadingBar.visibility = View.VISIBLE
                            errorText.visibility  = View.GONE
                            startBufferingWatchdogIfLive()
                        }
                        Player.STATE_READY -> {
                            loadingBar.visibility = View.GONE
                            errorText.visibility  = View.GONE
                            reconnectAttempts = 0
                            reconnectPending = false
                            cancelBufferingWatchdog()
                            ensurePreferredAudioTrackSelected()
                            handler.removeCallbacks(audioFallbackRunnable)
                            // FFmpeg/extension renderers can report audio support a moment after READY.
                            handler.postDelayed(audioFallbackRunnable, 900)
                            updatePlayPauseIcon()
                            val hasDuration = player.duration > 0
                            seekBar.visibility    = if (hasDuration) View.VISIBLE else View.GONE
                            seekSpacer.visibility = if (hasDuration) View.GONE   else View.VISIBLE
                            if (pendingResumeMs > 0L && hasDuration) {
                                val target = pendingResumeMs.coerceAtMost((player.duration - 500L).coerceAtLeast(0L))
                                player.seekTo(target)
                                pendingResumeMs = -1L
                            }
                        }
                        Player.STATE_ENDED -> {
                            loadingBar.visibility = View.GONE
                            cancelBufferingWatchdog()
                            if (isLiveStream && lastRequestedUrl.isNotBlank()) {
                                handler.post { restartLiveAfterSourceEnded() }
                            }
                        }
                        Player.STATE_IDLE -> {
                            // After stop() during channel change, IDLE appears before BUFFERING.
                            // Do not hide loading here — that caused the spinner to flash off.
                            cancelBufferingWatchdog()
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayPauseIcon()

                override fun onTracksChanged(tracks: Tracks) {
                    ensurePreferredAudioTrackSelected()
                    updateTrackButtonVisibility(tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    cancelBufferingWatchdog()
                    if (isLiveStream && error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        exoPlayer?.run {
                            seekToDefaultPosition()
                            prepare()
                            playWhenReady = true
                        }
                        return
                    }
                    if (retryWithAlternateSourceType()) return
                    if (retryWithAlternateLiveUrl()) return
                    val transientNetworkIssue = isTransientNetworkPlaybackError(error)
                    val allowedAttempts = when {
                        isLiveStream && transientNetworkIssue -> maxLiveReconnectAttempts
                        else -> maxReconnectAttempts
                    }
                    if (scheduleReconnect(allowedAttempts)) return
                    loadingBar.visibility = View.GONE
                    errorText.visibility  = View.VISIBLE
                    errorText.text        = "پەخشەکە بەردەست نییە"
                }
            })
        }
    }

    // ════════════════════════════════════════════════════════
    //  AUTO-RECONNECT
    // ════════════════════════════════════════════════════════
    private fun scheduleReconnect(maxAttempts: Int = if (isLiveStream) maxLiveReconnectAttempts else maxReconnectAttempts): Boolean {
        if (reconnectPending) return true
        if (reconnectAttempts >= maxAttempts) return false
        reconnectAttempts++
        val delay = if (isLiveStream) {
            (1200L + reconnectAttempts * 1200L).coerceAtMost(8000L)
        } else {
            (reconnectAttempts * 2000L).coerceAtMost(6000L)
        }
        errorText.visibility = View.VISIBLE
        errorText.text = "دووبارە پەیوەندی دەکرێت... (${reconnectAttempts}/$maxAttempts)"
        loadingBar.visibility = View.VISIBLE
        reconnectPending = true
        handler.removeCallbacks(reconnectRunnable)
        handler.postDelayed(reconnectRunnable, delay)
        return true
    }

    private fun attemptReconnect() {
        if (lastRequestedUrl.isBlank()) return
        cancelBufferingWatchdog()
        forceHlsForCurrentUrl = null
        sourceFallbackTriedForUrl = ""
        fallbackTriedForUrl = ""
        startPlayback(lastRequestedUrl, currentChannelName)
    }

    /** Live sources sometimes report ENDED when the playlist stops; reload the same URL. */
    private fun restartLiveAfterSourceEnded() {
        if (lastRequestedUrl.isBlank()) return
        reconnectAttempts = 0
        reconnectPending = false
        handler.removeCallbacks(reconnectRunnable)
        cancelBufferingWatchdog()
        forceHlsForCurrentUrl = null
        sourceFallbackTriedForUrl = ""
        fallbackTriedForUrl = ""
        startPlayback(lastRequestedUrl, currentChannelName)
    }

    private fun startBufferingWatchdogIfLive() {
        if (!isLiveStream) return
        handler.removeCallbacks(bufferingWatchdogRunnable)
        handler.postDelayed(bufferingWatchdogRunnable, LIVE_BUFFERING_WATCHDOG_MS)
    }

    private fun cancelBufferingWatchdog() {
        handler.removeCallbacks(bufferingWatchdogRunnable)
    }

    private fun onBufferingStallDetected() {
        val player = exoPlayer ?: return
        if (!isLiveStream || player.playbackState != Player.STATE_BUFFERING) return
        if (lastRequestedUrl.isBlank()) return
        scheduleReconnect()
    }

    private fun isTransientNetworkPlaybackError(error: PlaybackException): Boolean {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> true
            else -> false
        }
    }

    // ════════════════════════════════════════════════════════
    //  TRACK SELECTION (Audio / Subtitles / Quality)
    // ════════════════════════════════════════════════════════
    private fun updateTrackButtonVisibility(tracks: Tracks) {
        var hasMultiAudio = false
        var hasSubtitles = false
        var hasMultiVideo = false

        for (group in tracks.groups) {
            when (group.type) {
                // Show audio control whenever any audio track exists (single-track AC3/AACSwitch still useful)
                C.TRACK_TYPE_AUDIO -> if (group.mediaTrackGroup.length > 0) hasMultiAudio = true
                C.TRACK_TYPE_TEXT -> hasSubtitles = true
                C.TRACK_TYPE_VIDEO -> if (group.mediaTrackGroup.length > 1) hasMultiVideo = true
            }
        }
        btnAudioTrack.visibility = if (hasMultiAudio) View.VISIBLE else View.GONE
        btnSubtitle.visibility = if (hasSubtitles) View.VISIBLE else View.GONE
        // Always show quality — ABR presets (Auto/720p/1080p/4K) work even on single-track live streams
        btnQuality.visibility = View.VISIBLE
    }

    private fun showAudioTrackSelector() {
        val player = exoPlayer ?: return
        val audioTracks = mutableListOf<Pair<String, TrackSelectionOverride>>()

        for (group in player.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.mediaTrackGroup.length) {
                val format = group.mediaTrackGroup.getFormat(i)
                val label = format.label ?: format.language?.uppercase() ?: "ڕێچکە ${i + 1}"
                audioTracks.add(label to TrackSelectionOverride(group.mediaTrackGroup, i))
            }
        }

        if (audioTracks.isEmpty()) {
            Toast.makeText(this, "هیچ ڕێچکەی دەنگی بەردەست نییە", Toast.LENGTH_SHORT).show()
            return
        }

        val names = audioTracks.map { it.first }.toTypedArray()
        MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .setTitle("ڕێچکەی دەنگ")
            .setItems(names) { _, which ->
                val override = audioTracks[which].second
                trackSelector.parameters = trackSelector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .addOverride(override)
                    .build()
                showGenericOsd("دەنگ: ${names[which]}")
            }
            .show()
    }

    private fun showSubtitleSelector() {
        val player = exoPlayer ?: return
        val subtitleTracks = mutableListOf<Pair<String, TrackSelectionOverride>>()

        for (group in player.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (i in 0 until group.mediaTrackGroup.length) {
                val format = group.mediaTrackGroup.getFormat(i)
                val label = format.label ?: format.language?.uppercase() ?: "ژێرنووس ${i + 1}"
                subtitleTracks.add(label to TrackSelectionOverride(group.mediaTrackGroup, i))
            }
        }

        val names = mutableListOf("کوژاوە")
        names.addAll(subtitleTracks.map { it.first })

        MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .setTitle("ژێرنووس")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    trackSelector.parameters = trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    showGenericOsd("ژێرنووس: کوژاوە")
                } else {
                    val override = subtitleTracks[which - 1].second
                    trackSelector.parameters = trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .addOverride(override)
                        .build()
                    showGenericOsd("ژێرنووس: ${names[which]}")
                }
            }
            .show()
    }

    private fun showQualitySelector() {
        val player = exoPlayer ?: return
        val videoTracks = mutableListOf<Pair<String, TrackSelectionOverride>>()

        for (group in player.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) continue
            for (i in 0 until group.mediaTrackGroup.length) {
                val format = group.mediaTrackGroup.getFormat(i)
                val h = format.height
                val label = when {
                    h >= 2160 -> "4K (${h}p)"
                    h >= 1080 -> "1080p"
                    h >= 720  -> "720p"
                    h >= 480  -> "480p"
                    h > 0     -> "${h}p"
                    else      -> "ڕێچکە ${i + 1}"
                }
                videoTracks.add(label to TrackSelectionOverride(group.mediaTrackGroup, i))
            }
        }

        val names = mutableListOf(
            "سنووری ABR: خۆکار",
            "سنووردان: 720p",
            "سنووردان: 1080p",
            "سنووردان: 4K"
        )
        names.addAll(videoTracks.map { "ڕێچکە: ${it.first}" })

        MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .setTitle("کوالێتی ڤیدیۆ")
            .setItems(names.toTypedArray()) { _, which ->
                when {
                    which <= 3 -> applyVideoQualityPreset(which, showOsd = true)
                    else -> {
                        val override = videoTracks[which - 4].second
                        trackSelector.parameters = trackSelector.buildUponParameters()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .addOverride(override)
                            .build()
                        showGenericOsd("ڕێچکە: ${videoTracks[which - 4].first}")
                    }
                }
            }
            .show()
    }

    // ════════════════════════════════════════════════════════
    //  PLAYBACK SPEED
    // ════════════════════════════════════════════════════════
    private fun cyclePlaybackSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % speedValues.size
        exoPlayer?.setPlaybackSpeed(speedValues[currentSpeedIndex])
        btnSpeed.text = speedLabels[currentSpeedIndex]
        showGenericOsd("خێرایی: ${speedLabels[currentSpeedIndex]}")
        scheduleHideControls()
    }

    // ════════════════════════════════════════════════════════
    //  PLAYBACK
    // ════════════════════════════════════════════════════════
    private fun playUrl(url: String, name: String) {
        currentChannelName = name
        forceHlsForCurrentUrl = null
        sourceFallbackTriedForUrl = ""
        fallbackTriedForUrl = ""
        reconnectAttempts = 0
        reconnectPending = false
        cancelBufferingWatchdog()
        handler.removeCallbacks(reconnectRunnable)
        val currentChannel = channelList.firstOrNull { it.url == url.trim() }
        currentUserAgent = currentChannel?.userAgent?.trim().orEmpty().ifEmpty { intentUserAgent }
        currentReferrer = currentChannel?.referrer?.trim().orEmpty().ifEmpty { intentReferrer }
        pendingResumeMs = if (shouldTrackContinueWatching) {
            ContinueWatchingStore.getResumePositionMs(this, url.trim())
        } else {
            -1L
        }
        startPlayback(url.trim(), name)
    }

    private fun startPlayback(playbackUrl: String, name: String) {
        val client = okHttpClient ?: return
        try {
            loadingBar.visibility = View.VISIBLE
            errorText.visibility = View.GONE
            lastRequestedUrl = playbackUrl
            ensureAudibleVolume()
            exoPlayer?.volume = 1.0f
            SharedPrefManager(this).saveSPString(SharedPrefManager.SP_CURRENT_URL, playbackUrl)
            val src = buildMediaSource(playbackUrl, client, buildRequestHeaders())
            exoPlayer?.run { stop(); setMediaSource(src); prepare(); playWhenReady = true }
            tvChannel.text = name.uppercase()
            tvEpg.text     = "زانیاری نییە"
            cancelBufferingWatchdog()
        } catch (_: Exception) {
            errorText.text        = "نەتوانرا پەخشەکە باربکرێت"
            errorText.visibility  = View.VISIBLE
            loadingBar.visibility = View.GONE
            cancelBufferingWatchdog()
        }
    }

    private fun maybeRetryIfNoPlayableAudio() {
        val player = exoPlayer ?: return
        if (player.playbackState != Player.STATE_READY) return
        ensurePreferredAudioTrackSelected()
        if (!shouldFallbackForMissingAudio(player)) return
        if (retryWithAlternateSourceType()) return
        retryWithAlternateLiveUrl()
    }

    /** Pick the first ExoPlayer-supported audio track (helps TS with multiple PIDs / odd defaults). */
    private fun ensurePreferredAudioTrackSelected() {
        val player = exoPlayer ?: return
        var targetGroup: Tracks.Group? = null
        var targetIndex = -1
        for (group in player.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.mediaTrackGroup.length) {
                if (!group.isTrackSupported(i)) continue
                targetGroup = group
                targetIndex = i
                break
            }
            if (targetIndex >= 0) break
        }
        if (targetGroup == null || targetIndex < 0) return
        if (targetGroup.isTrackSelected(targetIndex)) return
        val override = TrackSelectionOverride(targetGroup.mediaTrackGroup, targetIndex)
        val next = trackSelector.buildUponParameters()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .addOverride(override)
            .build() as DefaultTrackSelector.Parameters
        trackSelector.parameters = next
    }

    private fun shouldFallbackForMissingAudio(player: ExoPlayer): Boolean {
        if (fallbackTriedForUrl == lastRequestedUrl) return false
        if (sourceFallbackTriedForUrl == lastRequestedUrl) return false
        for (group in player.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.mediaTrackGroup.length) {
                if (group.isTrackSupported(i) && group.isTrackSelected(i)) return false
            }
        }
        // No audio in media, or only unsupported codecs / nothing selected yet at READY
        return true
    }

    private fun retryWithAlternateSourceType(): Boolean {
        val current = lastRequestedUrl
        if (current.isBlank()) return false
        if (sourceFallbackTriedForUrl == current) return false
        val isCurrentlyHls = shouldUseHls(current)
        sourceFallbackTriedForUrl = current
        forceHlsForCurrentUrl = !isCurrentlyHls
        return try {
            startPlayback(current, currentChannelName)
            true
        } catch (_: Exception) { false }
    }

    private fun retryWithAlternateLiveUrl(): Boolean {
        val current = lastRequestedUrl
        if (current.isBlank()) return false
        if (fallbackTriedForUrl == current) return false
        val alt = alternateLiveUrl(current) ?: return false
        fallbackTriedForUrl = current
        forceHlsForCurrentUrl = null
        sourceFallbackTriedForUrl = ""
        return try {
            startPlayback(alt, currentChannelName)
            true
        } catch (_: Exception) { false }
    }

    private fun buildMediaSource(
        playbackUrl: String,
        client: OkHttpClient,
        headers: Map<String, String>
    ): androidx.media3.exoplayer.source.MediaSource {
        val uri = Uri.parse(playbackUrl)
        val lower = playbackUrl.lowercase(Locale.US)
        val item = when {
            lower.endsWith(".ts") || lower.contains(".ts?") ->
                MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.VIDEO_MP2T)
                    .build()
            else -> MediaItem.fromUri(uri)
        }
        val dataFactory = OkHttpDataSource.Factory(client).apply {
            val ua = headers["User-Agent"]
            if (!ua.isNullOrEmpty()) setUserAgent(ua)
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
        }
        return if (shouldUseHls(playbackUrl)) {
            HlsMediaSource.Factory(dataFactory).createMediaSource(item)
        } else {
            ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this, dataFactory))
                .createMediaSource(item)
        }
    }

    private fun shouldUseHls(playbackUrl: String): Boolean {
        forceHlsForCurrentUrl?.let { return it }
        val lower = playbackUrl.lowercase(Locale.US)
        if (lower.contains(".m3u8") || lower.contains(".m3u")) return true
        if (lower.contains("type=m3u") || lower.contains("output=hls")) return true
        return when (Util.inferContentType(Uri.parse(playbackUrl))) {
            C.CONTENT_TYPE_HLS -> true
            else -> false
        }
    }

    private fun buildRequestHeaders(): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        val ua = currentUserAgent.trim().ifEmpty { "VLC/3.0.20 LibVLC/3.0.20" }
        headers["User-Agent"] = ua
        val ref = currentReferrer.trim()
        if (ref.isNotEmpty()) {
            headers["Referer"] = ref
            toOrigin(ref)?.let { headers["Origin"] = it }
        }
        return headers
    }

    private fun toOrigin(url: String): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val defaultPort = when (httpUrl.scheme.lowercase(Locale.US)) {
            "https" -> 443; "http" -> 80; else -> -1
        }
        return "${httpUrl.scheme}://${httpUrl.host}" + if (httpUrl.port != defaultPort) ":${httpUrl.port}" else ""
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
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false)
            }
            if (maxVol > 0 && curVol <= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.40f).toInt().coerceAtLeast(2), 0)
            }
            exoPlayer?.volume = 1.0f
        } catch (_: Exception) {}
    }

    // ════════════════════════════════════════════════════════
    //  CONTROLS UI
    // ════════════════════════════════════════════════════════
    private fun bindViews() {
        playerView      = findViewById(R.id.player_view)
        loadingBar      = findViewById(R.id.player_loading)
        errorText       = findViewById(R.id.player_error)
        topBar          = findViewById(R.id.player_top_bar)
        bottomBar       = findViewById(R.id.player_bottom_bar)
        btnBack         = findViewById(R.id.btn_back)
        btnPlayPause    = findViewById(R.id.btn_play_pause)
        btnPrev         = findViewById(R.id.btn_prev_channel)
        btnNext         = findViewById(R.id.btn_next_channel)
        btnResize       = findViewById(R.id.btn_resize)
        btnAudioTrack   = findViewById(R.id.btn_audio_track)
        btnSubtitle     = findViewById(R.id.btn_subtitle)
        btnSpeed        = findViewById(R.id.btn_speed)
        btnQuality      = findViewById(R.id.btn_quality)
        resizeOsd       = findViewById(R.id.resize_osd)
        channelNumberOsd = findViewById(R.id.channel_number_osd)
        seekBar         = findViewById(R.id.seek_bar)
        seekSpacer      = findViewById(R.id.seek_spacer)
        tvChannel       = findViewById(R.id.tv_current_channel)
        tvEpg           = findViewById(R.id.tv_current_epg)
        tvClock         = findViewById(R.id.tv_clock)
        volumeOsd       = findViewById(R.id.volume_osd)
        osdIcon         = volumeOsd.findViewById(R.id.osd_volume_icon)
        osdPercent      = volumeOsd.findViewById(R.id.osd_volume_percent)
        osdBar          = volumeOsd.findViewById(R.id.osd_volume_bar)

        btnAudioTrack.visibility = View.GONE
        btnSubtitle.visibility = View.GONE
    }

    /** Android TV / leanback: SurfaceView avoids TextureView+GPU path that often shows green decode artifacts. */
    private fun isTelevisionUi(): Boolean {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        val uiModeType = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return uiModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun setupControls() {
        playerView.setOnClickListener { toggleControls() }
        btnBack.setOnClickListener { finish() }
        btnPlayPause.setOnClickListener { togglePlayPause(); scheduleHideControls() }
        btnPrev.setOnClickListener { prevChannel() }
        btnNext.setOnClickListener { nextChannel() }
        btnResize.setOnClickListener { cycleResizeMode() }
        btnAudioTrack.setOnClickListener { showAudioTrackSelector() }
        btnSubtitle.setOnClickListener { showSubtitleSelector() }
        btnSpeed.setOnClickListener { cyclePlaybackSpeed() }
        btnQuality.setOnClickListener { showQualitySelector() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) { seekbarUserDragging = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                seekbarUserDragging = false
                val p = exoPlayer ?: return
                if (p.duration > 0L) {
                    val pos = p.duration * ((sb?.progress ?: 0).toLong()) / 1000L
                    p.seekTo(pos.coerceIn(0L, p.duration))
                }
            }
        })

        listOf(
            btnBack,
            btnPrev, btnPlayPause, btnNext, btnAudioTrack, btnSubtitle,
            btnSpeed, btnQuality, btnResize
        ).forEach {
            it.isFocusable = true
            it.isFocusableInTouchMode = true
        }
    }

    private fun isFocusInsideBottomBar(): Boolean {
        var v: View? = currentFocus
        while (v != null) {
            if (v === bottomBar) return true
            v = v.parent as? View
        }
        return false
    }

    private fun focusBottomControls() {
        showControls()
        val target = listOf(
            btnPlayPause, btnNext, btnPrev, btnAudioTrack, btnSubtitle,
            btnSpeed, btnQuality, btnResize
        ).firstOrNull { it.visibility == View.VISIBLE && it.isFocusable }
        target?.requestFocus()
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
        if (isInPipMode) return
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
    //  RESIZE MODE
    // ════════════════════════════════════════════════════════
    private fun cycleResizeMode() {
        currentResizeIndex = (currentResizeIndex + 1) % resizeModes.size
        playerView.resizeMode = resizeModes[currentResizeIndex]
        showGenericOsd(resizeLabels[currentResizeIndex])
        scheduleHideControls()
    }

    private fun showGenericOsd(label: String) {
        resizeOsd.text = label
        resizeOsd.visibility = View.VISIBLE
        resizeOsd.animate().cancel()
        resizeOsd.animate().alpha(1f).setDuration(150).start()
        handler.removeCallbacks(hideResizeOsdRunnable)
        handler.postDelayed(hideResizeOsdRunnable, 1500L)
    }

    private fun fadeOutResizeOsd() {
        resizeOsd.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction { resizeOsd.visibility = View.INVISIBLE }
            .start()
    }

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.hide(WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        supportActionBar?.hide()
    }
}

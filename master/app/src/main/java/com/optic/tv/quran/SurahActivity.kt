package com.optic.tv.quran

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.optic.tv.BaseThemedAppCompatActivity
import com.optic.tv.R
import com.optic.tv.databinding.ActivitySurahBinding
import com.optic.tv.utils.ThemeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SurahActivity : BaseThemedAppCompatActivity() {

    private lateinit var binding: ActivitySurahBinding
    private var exoPlayer: ExoPlayer? = null
    private var adapter: AyahAdapter? = null
    private var hasEverPlayed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurahBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeHelper.applyPremiumHeroBackground(binding.root)

        val surahId = intent.getIntExtra("SURAH_ID", 1)
        val nameEn = intent.getStringExtra("SURAH_NAME_EN")
            ?: intent.getStringExtra("SURAH_NAME")
            .orEmpty()
        val nameAr = intent.getStringExtra("SURAH_NAME_AR").orEmpty()
        val revelation = intent.getStringExtra("SURAH_REVELATION").orEmpty()
        val ayahCountHint = intent.getIntExtra("SURAH_AYAH_COUNT", -1)

        binding.tvSurahNameEn.text = nameEn.ifEmpty { getString(R.string.quran_title) }
        binding.tvSurahName.text = nameAr.ifEmpty { nameEn }
        binding.tvSurahMeta.text = if (ayahCountHint >= 0) {
            getString(
                R.string.quran_surah_meta,
                localizedRevelation(revelation),
                ayahCountHint
            )
        } else {
            ""
        }

        binding.rvAyahs.layoutManager = LinearLayoutManager(this)
        binding.tvPlayerStatus.text = getString(R.string.quran_reciter)

        binding.ivBack.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { fetchSurahDetails(surahId) }

        initPlayer()
        fetchSurahDetails(surahId)
    }

    private fun localizedRevelation(raw: String): String {
        return when (raw.lowercase(Locale.US)) {
            "meccan" -> getString(R.string.quran_revelation_meccan)
            "medinan" -> getString(R.string.quran_revelation_medinan)
            else -> raw.ifEmpty { "—" }
        }
    }

    private fun initPlayer() {
        val ivPlayPause = binding.ivPlayPause
        val tvPlayerStatus = binding.tvPlayerStatus
        val rvAyahs = binding.rvAyahs

        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                val currentIndex = exoPlayer?.currentMediaItemIndex ?: -1
                adapter?.currentPlayingIndex = currentIndex
                if (currentIndex >= 0) {
                    rvAyahs.smoothScrollToPosition(currentIndex)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) {
                    hasEverPlayed = true
                    ivPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    tvPlayerStatus.text = getString(R.string.quran_playing_reciter)
                } else {
                    ivPlayPause.setImageResource(android.R.drawable.ic_media_play)
                    tvPlayerStatus.text = if (hasEverPlayed) {
                        getString(R.string.quran_paused)
                    } else {
                        getString(R.string.quran_reciter)
                    }
                }
            }
        })

        ivPlayPause.setOnClickListener {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
        }
    }

    private fun fetchSurahDetails(surahId: Int) {
        binding.pbLoading.visibility = View.VISIBLE
        binding.llError.visibility = View.GONE
        binding.rvAyahs.visibility = View.VISIBLE
        binding.cardPlayer.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    QuranApiClient.api.getSurahWithAudio(surahId)
                }
                binding.pbLoading.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!.data
                    binding.tvSurahName.text = data.name
                    binding.tvSurahNameEn.text = data.englishName
                    val revelation = intent.getStringExtra("SURAH_REVELATION").orEmpty()
                    binding.tvSurahMeta.text = getString(
                        R.string.quran_surah_meta,
                        localizedRevelation(revelation),
                        data.ayahs.size
                    )
                    setupListAndPlayer(data.ayahs)
                } else {
                    showSurahError(getString(R.string.quran_surah_load_error))
                }
            } catch (e: Exception) {
                binding.pbLoading.visibility = View.GONE
                showSurahError(
                    getString(R.string.quran_error_with_message, e.message ?: "")
                )
            }
        }
    }

    private fun showSurahError(message: String) {
        binding.llError.visibility = View.VISIBLE
        binding.rvAyahs.visibility = View.GONE
        binding.cardPlayer.visibility = View.GONE
        binding.tvError.text = message
    }

    private fun setupListAndPlayer(ayahs: List<Ayah>) {
        adapter = AyahAdapter(ayahs) { index ->
            exoPlayer?.seekToDefaultPosition(index)
            exoPlayer?.play()
        }
        binding.rvAyahs.adapter = adapter

        val mediaItems = ayahs.mapNotNull { ayah ->
            ayah.audio?.let { MediaItem.fromUri(it) }
        }
        exoPlayer?.setMediaItems(mediaItems)
        exoPlayer?.prepare()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}

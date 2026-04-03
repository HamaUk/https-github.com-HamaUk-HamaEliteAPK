package com.bachors.iptv.quran

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bachors.iptv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SurahActivity : AppCompatActivity() {

    private lateinit var rvAyahs: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvSurahName: TextView
    private lateinit var tvPlayerStatus: TextView
    private lateinit var ivPlayPause: ImageView

    private var exoPlayer: ExoPlayer? = null
    private var adapter: AyahAdapter? = null

    private val BASE_URL = "https://api.alquran.cloud/v1/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surah)

        val surahId = intent.getIntExtra("SURAH_ID", 1)
        val surahName = intent.getStringExtra("SURAH_NAME") ?: "Surah"

        tvSurahName = findViewById(R.id.tv_surah_name)
        rvAyahs = findViewById(R.id.rv_ayahs)
        pbLoading = findViewById(R.id.pb_loading)
        tvPlayerStatus = findViewById(R.id.tv_player_status)
        ivPlayPause = findViewById(R.id.iv_play_pause)

        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        tvSurahName.text = surahName
        rvAyahs.layoutManager = LinearLayoutManager(this)

        initPlayer()
        fetchSurahDetails(surahId)
    }

    private fun initPlayer() {
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
                    ivPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    tvPlayerStatus.text = "Playing: Mishary Alafasy"
                } else {
                    ivPlayPause.setImageResource(android.R.drawable.ic_media_play)
                    tvPlayerStatus.text = "Paused"
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
        pbLoading.visibility = View.VISIBLE
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(QuranApi::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.getSurahWithAudio(surahId)
                withContext(Dispatchers.Main) {
                    pbLoading.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val ayahs = response.body()!!.data.ayahs
                        setupListAndPlayer(ayahs)
                    } else {
                        Toast.makeText(this@SurahActivity, "Failed to fetch Surah", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pbLoading.visibility = View.GONE
                    Toast.makeText(this@SurahActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupListAndPlayer(ayahs: List<Ayah>) {
        adapter = AyahAdapter(ayahs) { index ->
            // Update exoPlayer track
            exoPlayer?.seekToDefaultPosition(index)
            exoPlayer?.play()
        }
        rvAyahs.adapter = adapter

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

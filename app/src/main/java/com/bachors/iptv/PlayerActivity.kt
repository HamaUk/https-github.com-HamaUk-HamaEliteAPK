package com.bachors.iptv

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.khizar1556.mkvideoplayer.MKPlayer
import com.bachors.iptv.adapters.ChannelsAdapter
import com.bachors.iptv.utils.RecyclerTouchListener
import com.bachors.iptv.utils.SharedPrefManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class PlayerActivity : AppCompatActivity() {
    private lateinit var con: Context
    private var mkPlayer: MKPlayer? = null
    private lateinit var channelAdapter: ChannelsAdapter
    private val handler = Handler(Looper.getMainLooper())
    
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000 * 60) // Update every minute
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        supportActionBar?.hide()

        con = this

        val decorView = window.decorView
        val wic = WindowInsetsControllerCompat(window, decorView)
        wic.isAppearanceLightStatusBars = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            decorView.isForceDarkAllowed = true
        }

        val b = intent.extras
        val name = b?.getString("name") ?: "Channel"
        val url = b?.getString("url") ?: ""

        if (url.isEmpty()) {
            Toast.makeText(this, "Error: Invalid playback URL", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupUI(name)
        mkPlayer = MKPlayer(this)
        initPlayer(url, name)
        startClock()
    }

    private fun setupUI(currentName: String) {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        
        // Metadata
        findViewById<TextView>(R.id.tv_current_channel).text = currentName.uppercase()
        findViewById<TextView>(R.id.tv_current_epg).text = "No Information"

        // Sidebar Channels
        channelAdapter = ChannelsAdapter(this)
        val rv = findViewById<RecyclerView>(R.id.rv_player_channels)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = channelAdapter

        // Load channels from SharedPrefs (saved in PlaylistActivity)
        val sharedPrefManager = SharedPrefManager(this)
        val channelsJson = sharedPrefManager.getSpChannels()
        if (channelsJson.isNotEmpty() && channelsJson != "[]") {
            try {
                val gson = Gson()
                val listType = object : TypeToken<List<com.bachors.iptv.models.ChannelsData>>() {}.type
                val channels: List<com.bachors.iptv.models.ChannelsData> = gson.fromJson(channelsJson, listType)
                channelAdapter.addAll(channels)
            } catch (e: Exception) {
                // Ignore parsing errors from old/corrupt caches, but prevent the crash!
                e.printStackTrace()
            }
        }

        // Sidebar Click
        rv.addOnItemTouchListener(RecyclerTouchListener(this, rv, object : RecyclerTouchListener.ClickListener {
            override fun onClick(view: View, position: Int) {
                val data = channelAdapter.getItem(position)
                initPlayer(data.url, data.name)
                findViewById<TextView>(R.id.tv_current_channel).text = data.name.uppercase()
            }
            override fun onLongClick(view: View, position: Int) {}
        }))
    }

    private fun initPlayer(url: String, name: String) {
        try {
            mkPlayer?.play(url)
            mkPlayer?.setTitle(name)
            mkPlayer?.setPlayerCallbacks(object : MKPlayer.playerCallbacks {
                override fun onNextClick() {}
                override fun onPreviousClick() {}
            })
        } catch (e: Exception) {
            // Log the error but DO NOT call finish() — the player will show "small problem" instead
            e.printStackTrace()
        }
    }

    private fun startClock() {
        handler.post(clockRunnable)
    }

    private fun updateClock() {
        val sdf = SimpleDateFormat("HH:mm a | MMM dd yyyy", Locale.getDefault())
        val currentTime = sdf.format(Date())
        findViewById<TextView>(R.id.tv_clock).text = currentTime
    }

    override fun onPause() {
        super.onPause()
        mkPlayer?.onPause()
    }

    override fun onResume() {
        super.onResume()
        mkPlayer?.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        mkPlayer?.onDestroy()
        handler.removeCallbacks(clockRunnable)
    }
}
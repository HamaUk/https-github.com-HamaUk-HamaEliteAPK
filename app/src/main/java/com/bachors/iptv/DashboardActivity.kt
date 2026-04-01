package com.bachors.iptv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bachors.iptv.databinding.ActivityDashboardBinding
import com.bachors.iptv.models.PlaylistData
import com.bachors.iptv.utils.SharedPrefManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityDashboardBinding.inflate(layoutInflater)
            setContentView(binding.root)

            supportActionBar?.hide()
            setupClock()
            setupClickListeners()
            updateCategoryCounts()
        } catch (t: Throwable) {
            t.printStackTrace()
            Toast.makeText(this, "لە کێشەی دەستپێک ڕزگاربووین. تکایە دووبارە بچۆ ژوورەوە.", Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateCategoryCounts()
    }

    private fun setupClock() {
        val runnable = object : Runnable {
            override fun run() {
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                binding.tvClock.text = sdf.format(Date())
                handler.postDelayed(this, 60000)
            }
        }
        handler.post(runnable)
    }

    private fun setupClickListeners() {
        binding.cardLive.setOnClickListener {
            val intent = Intent(this, PlaylistActivity::class.java)
            intent.putExtra("type", "live")
            startActivity(intent)
        }

        binding.cardVod.setOnClickListener {
            val intent = Intent(this, PlaylistActivity::class.java)
            intent.putExtra("type", "vod")
            startActivity(intent)
        }

        binding.cardSeries.setOnClickListener {
            val intent = Intent(this, PlaylistActivity::class.java)
            intent.putExtra("type", "series")
            startActivity(intent)
        }

        binding.cardEpg.setOnClickListener {
            val intent = Intent(this, PlaylistActivity::class.java)
            intent.putExtra("type", "live")
            intent.putExtra("epg", true)
            startActivity(intent)
        }

        binding.cardSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateCategoryCounts() {
        try {
            val sp = SharedPrefManager(this)
            val rawJson = sp.getSpPlaylist()
            if (rawJson.isNotEmpty() && rawJson != "[]") {
                val listType = object : TypeToken<List<PlaylistData>>() {}.type
                val data: List<PlaylistData> = try {
                    Gson().fromJson(rawJson, listType)
                } catch (_: Exception) {
                    val outer = org.json.JSONArray(rawJson)
                    Gson().fromJson(outer.getJSONArray(0).toString(), listType)
                }
                val totalGroups = data.count { it.title.isNotEmpty() }
                val totalChannels = data.sumOf { it.channel.toIntOrNull() ?: 0 }
                binding.tvLabelLive.text = if (totalChannels > 0) "پەخشی ڕاستەوخۆ ($totalChannels)" else "پەخشی ڕاستەوخۆ"
                binding.tvLabelPlaylists.text = if (totalGroups > 0) "پلی‌لیستەکان ($totalGroups)" else "پلی‌لیستەکان"
            }

            val favJson = sp.getSpFavorites()
            if (favJson.isNotEmpty() && favJson != "[]") {
                val favCount = org.json.JSONArray(favJson).length()
                if (favCount > 0) {
                    binding.tvLabelPlaylists.text = "دڵخوازەکان ($favCount)"
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

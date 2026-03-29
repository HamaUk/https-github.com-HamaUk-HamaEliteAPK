package com.bachors.iptv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bachors.iptv.databinding.ActivityDashboardBinding
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
        } catch (t: Throwable) {
            // TV sticks can fail on unexpected resource/runtime state; recover to login instead of hard crash.
            t.printStackTrace()
            Toast.makeText(this, "Recovered from startup issue. Please login again.", Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
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
            // EPG/Catchup usually filtered live
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

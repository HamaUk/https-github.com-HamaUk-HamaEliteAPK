package com.bachors.iptv.sports

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bachors.iptv.BaseThemedAppCompatActivity
import com.bachors.iptv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SportsActivity : BaseThemedAppCompatActivity() {

    private lateinit var rvLeagues: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private val job = SupervisorJob()
    private val uiScope = CoroutineScope(job + Dispatchers.Main)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val fetchRunnable = object : Runnable {
        override fun run() {
            fetchSportsData()
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sports)

        findViewById<android.widget.TextView>(R.id.tv_sports_title).text =
            getString(R.string.sports_title)

        rvLeagues = findViewById(R.id.rv_sports_leagues)
        rvLeagues.layoutManager = LinearLayoutManager(this)
        pbLoading = findViewById(R.id.pb_loading)

        findViewById<android.widget.ImageView>(R.id.iv_back).setOnClickListener { finish() }

        handler.post(fetchRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        job.cancel()
    }

    private fun fetchSportsData() {
        pbLoading.visibility = View.VISIBLE
        uiScope.launch {
            try {
                val leagues = withContext(Dispatchers.IO) {
                    YsScoresRepository.fetchLiveLeagues()
                }
                pbLoading.visibility = View.GONE
                if (leagues.isEmpty()) {
                    Toast.makeText(this@SportsActivity, R.string.sports_no_live, Toast.LENGTH_SHORT).show()
                    rvLeagues.adapter = SportsAdapter(emptyList())
                } else {
                    rvLeagues.adapter = SportsAdapter(leagues)
                }
            } catch (e: Exception) {
                pbLoading.visibility = View.GONE
                Toast.makeText(
                    this@SportsActivity,
                    getString(R.string.sports_load_error, e.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

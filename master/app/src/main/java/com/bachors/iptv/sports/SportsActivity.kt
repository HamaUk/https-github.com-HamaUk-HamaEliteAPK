package com.bachors.iptv.sports

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bachors.iptv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SportsActivity : AppCompatActivity() {

    private lateinit var rvLeagues: RecyclerView
    private lateinit var pbLoading: ProgressBar

    // Using Android emulator default loopback. 
    // If testing on a real device, replace this with your computer's local IP (e.g. 192.168.1.5)
    private val BASE_URL = "http://10.0.2.2:3000"

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val fetchRunnable = object : Runnable {
        override fun run() {
            fetchSportsData()
            handler.postDelayed(this, 30000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sports)

        rvLeagues = findViewById(R.id.rv_sports_leagues)
        rvLeagues.layoutManager = LinearLayoutManager(this)
        pbLoading = findViewById(R.id.pb_loading)

        handler.post(fetchRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun fetchSportsData() {
        pbLoading.visibility = View.VISIBLE
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(SportsApi::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.getMatches()
                withContext(Dispatchers.Main) {
                    pbLoading.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val adapter = SportsAdapter(response.body()!!)
                        rvLeagues.adapter = adapter
                    } else {
                        Toast.makeText(this@SportsActivity, "Failed to load matches", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pbLoading.visibility = View.GONE
                    Toast.makeText(this@SportsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

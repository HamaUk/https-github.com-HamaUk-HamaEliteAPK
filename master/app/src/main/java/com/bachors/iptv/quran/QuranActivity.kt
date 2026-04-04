package com.bachors.iptv.quran

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import com.bachors.iptv.BaseThemedAppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bachors.iptv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class QuranActivity : BaseThemedAppCompatActivity() {

    private lateinit var rvSurahs: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private val BASE_URL = "https://api.alquran.cloud/v1/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quran)

        rvSurahs = findViewById(R.id.rv_quran_surahs)
        rvSurahs.layoutManager = LinearLayoutManager(this)
        pbLoading = findViewById(R.id.pb_loading)

        findViewById<android.widget.ImageView>(R.id.iv_back).setOnClickListener { finish() }

        fetchSurahs()
    }

    private fun fetchSurahs() {
        pbLoading.visibility = View.VISIBLE
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(QuranApi::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.getAllSurahs()
                withContext(Dispatchers.Main) {
                    pbLoading.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val adapter = QuranAdapter(response.body()!!.data)
                        rvSurahs.adapter = adapter
                    } else {
                        Toast.makeText(this@QuranActivity, "Failed to load Quran", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pbLoading.visibility = View.GONE
                    Toast.makeText(this@QuranActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

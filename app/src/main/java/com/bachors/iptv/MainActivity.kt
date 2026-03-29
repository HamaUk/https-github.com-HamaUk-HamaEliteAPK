package com.bachors.iptv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bachors.iptv.databinding.ActivityMainBinding
import com.bachors.iptv.utils.IptvService
import com.bachors.iptv.utils.SharedPrefManager
import com.bachors.iptv.utils.SyncData
import com.bachors.iptv.utils.XtreamAuthResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Random

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefManager: SharedPrefManager
    private var deviceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide() // Fullscreen experience
        sharedPrefManager = SharedPrefManager(this)
        
        setupDeviceId()
        setupTabs()
        setupClickListeners()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun setupDeviceId() {
        // Pure 8-digit numeric code
        val prefs = getSharedPreferences("hk_prefs", Context.MODE_PRIVATE)
        deviceId = prefs.getString("hk_device_id", "") ?: ""
        if (deviceId.isEmpty() || deviceId.length != 8) {
            val random = Random()
            deviceId = (10000000 + random.nextInt(90000000)).toString()
            prefs.edit().putString("hk_device_id", deviceId).apply()
        }
        
        // Format as 1234 5678 for readability
        val formattedCode = if (deviceId.length == 8) {
            "${deviceId.substring(0, 4)} ${deviceId.substring(4)}"
        } else {
            deviceId
        }
        binding.tvDeviceCode.text = formattedCode
    }

    private fun setupTabs() {
        binding.btnTabSync.setOnClickListener { showLayout("sync") }
        binding.btnTabXtream.setOnClickListener { showLayout("xtream") }
        binding.btnTabM3u.setOnClickListener { showLayout("m3u") }
    }

    private fun showLayout(type: String) {
        binding.layoutSync.visibility = if (type == "sync") View.VISIBLE else View.GONE
        binding.layoutXtream.visibility = if (type == "xtream") View.VISIBLE else View.GONE
        binding.layoutM3u.visibility = if (type == "m3u") View.VISIBLE else View.GONE
        
        // Alpha feedback for active tab
        binding.btnTabSync.alpha = if (type == "sync") 1.0f else 0.5f
        binding.btnTabXtream.alpha = if (type == "xtream") 1.0f else 0.5f
        binding.btnTabM3u.alpha = if (type == "m3u") 1.0f else 0.5f
    }

    private fun setupClickListeners() {
        binding.btnRefreshSync.setOnClickListener { performSync() }
        
        binding.btnLoginXtream.setOnClickListener {
            val server = binding.etServer.text.toString()
            val user = binding.etUsername.text.toString()
            val pass = binding.etPassword.text.toString()
            
            if (server.isNotEmpty() && user.isNotEmpty()) {
                performXtreamLogin(server, user, pass)
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnLoginM3u.setOnClickListener {
            val url = binding.etM3uUrl.text.toString()
            if (url.isNotEmpty()) {
                navigateToDashboard()
            }
        }
    }

    private fun performSync() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://hama-elite-sync.web.app/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(IptvService::class.java)
        service.getSyncData(deviceId).enqueue(object : Callback<SyncData> {
            override fun onResponse(call: Call<SyncData>, response: Response<SyncData>) {
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    Toast.makeText(this@MainActivity, "Sync Successful!", Toast.LENGTH_SHORT).show()
                    navigateToDashboard()
                } else {
                    Toast.makeText(this@MainActivity, "No data found for this code", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<SyncData>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Sync Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun performXtreamLogin(server: String, user: String, pass: String) {
        // Simple Xtream Login Check
        val baseUrl = if (server.endsWith("/")) server else "$server/"
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(IptvService::class.java)
        service.loginXtream(user, pass).enqueue(object : Callback<XtreamAuthResponse> {
            override fun onResponse(call: Call<XtreamAuthResponse>, response: Response<XtreamAuthResponse>) {
                if (response.isSuccessful && response.body()?.userInfo != null) {
                    navigateToDashboard()
                } else {
                    Toast.makeText(this@MainActivity, "Authentication Failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<XtreamAuthResponse>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Login Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}
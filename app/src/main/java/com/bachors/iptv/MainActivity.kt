package com.bachors.iptv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bachors.iptv.databinding.ActivityMainBinding
import com.bachors.iptv.utils.IptvService
import com.bachors.iptv.utils.SharedPrefManager
import com.bachors.iptv.utils.SyncData
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefManager: SharedPrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        sharedPrefManager = SharedPrefManager(this)

        val alreadyLoggedIn = sharedPrefManager.getSpM3uDirect().isNotEmpty() ||
            sharedPrefManager.getSpPlaylist().let { it.isNotEmpty() && it != "[]" }
        if (alreadyLoggedIn) {
            runCatching {
                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
                return
            }.onFailure {
                Toast.makeText(this, R.string.auto_resume_failed, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnStart.setOnClickListener { performSync() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun performSync() {
        binding.btnStart.isEnabled = false
        binding.progressSync.visibility = View.VISIBLE

        val retrofit = Retrofit.Builder()
            .baseUrl("https://hama-elite-sync-default-rtdb.firebaseio.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(IptvService::class.java)
        service.getSyncData(GLOBAL_SYNC_KEY).enqueue(object : Callback<SyncData> {
            override fun onResponse(call: Call<SyncData>, response: Response<SyncData>) {
                binding.btnStart.isEnabled = true
                binding.progressSync.visibility = View.GONE
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val synced = applySyncedConfig(body)
                    if (synced) {
                        Toast.makeText(this@MainActivity, R.string.sync_success, Toast.LENGTH_SHORT).show()
                        navigateToDashboard()
                    } else {
                        Toast.makeText(this@MainActivity, R.string.sync_invalid, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, R.string.sync_no_data, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<SyncData>, t: Throwable) {
                binding.btnStart.isEnabled = true
                binding.progressSync.visibility = View.GONE
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.sync_error, t.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    private fun applySyncedConfig(data: SyncData): Boolean {
        val method = data.method?.trim()?.lowercase() ?: ""
        return when {
            !data.content.isNullOrBlank() -> {
                sharedPrefManager.saveSPString(
                    SharedPrefManager.SP_M3U_DIRECT,
                    "file_content:${data.content}"
                )
                sharedPrefManager.saveSPString(SharedPrefManager.SP_PLAYLIST, "[]")
                true
            }
            method == "m3u" && !data.url.isNullOrBlank() -> {
                sharedPrefManager.saveSPString(SharedPrefManager.SP_M3U_DIRECT, data.url.trim())
                sharedPrefManager.saveSPString(SharedPrefManager.SP_PLAYLIST, "[]")
                true
            }
            method == "xtream" && !data.server.isNullOrBlank() && !data.user.isNullOrBlank() && !data.pass.isNullOrBlank() -> {
                val m3uUrl = buildXtreamM3uUrl(data.server, data.user, data.pass)
                sharedPrefManager.saveSPString(SharedPrefManager.SP_M3U_DIRECT, m3uUrl)
                sharedPrefManager.saveSPString(SharedPrefManager.SP_PLAYLIST, "[]")
                true
            }
            !data.url.isNullOrBlank() -> {
                sharedPrefManager.saveSPString(SharedPrefManager.SP_M3U_DIRECT, data.url.trim())
                sharedPrefManager.saveSPString(SharedPrefManager.SP_PLAYLIST, "[]")
                true
            }
            else -> false
        }
    }

    private fun normalizeServer(server: String): String {
        val trimmed = server.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "http://$trimmed"
    }

    private fun buildXtreamM3uUrl(server: String, user: String, pass: String): String {
        val cleanServer = normalizeServer(server).trimEnd('/')
        return "$cleanServer/get.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}&type=m3u_plus&output=mpegts"
    }

    companion object {
        /** Shared Firebase RTDB key — same as portal `index.html` (sync/global). */
        const val GLOBAL_SYNC_KEY = "global"
    }
}

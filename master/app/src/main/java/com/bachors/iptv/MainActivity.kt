package com.bachors.iptv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bachors.iptv.databinding.ActivityMainBinding
import com.bachors.iptv.utils.GlobalSync
import com.bachors.iptv.utils.IptvService
import com.bachors.iptv.utils.SharedPrefManager
import com.bachors.iptv.utils.SyncData
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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

        val service = GlobalSync.retrofit().create(IptvService::class.java)
        service.getSyncData(GlobalSync.SYNC_KEY_GLOBAL).enqueue(object : Callback<SyncData> {
            override fun onResponse(call: Call<SyncData>, response: Response<SyncData>) {
                binding.btnStart.isEnabled = true
                binding.progressSync.visibility = View.GONE
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val synced = GlobalSync.applySyncedConfig(this@MainActivity, sharedPrefManager, body)
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

    companion object {
        /** Shared Firebase RTDB key — same as portal `index.html` (sync/global). */
        const val GLOBAL_SYNC_KEY = GlobalSync.SYNC_KEY_GLOBAL
    }
}

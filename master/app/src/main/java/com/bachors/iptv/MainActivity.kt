package com.bachors.iptv

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bachors.iptv.databinding.ActivityMainBinding
import com.bachors.iptv.utils.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefManager: SharedPrefManager
    private var deviceCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        sharedPrefManager = SharedPrefManager(this)
        deviceCode = ActivationHelper.getDeviceCode(this)
        
        val formattedCode = if (deviceCode.length == 8) {
            "${deviceCode.substring(0, 4)} ${deviceCode.substring(4)}"
        } else {
            deviceCode
        }
        binding.txtDeviceCode.text = formattedCode
        
        // Setup Button
        binding.btnRetry.setOnClickListener {
            performAutoSync()
        }

        // Start Auto-Sync immediately on launch
        performAutoSync()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun performAutoSync() {
        binding.btnRetry.visibility = View.GONE
        binding.progressSync.visibility = View.VISIBLE
        binding.txtStatus.text = "Checking activation..."
        binding.txtStatus.setTextColor(Color.WHITE)

        val service = GlobalSync.retrofit().create(IptvService::class.java)
        service.getSyncData(deviceCode).enqueue(object : Callback<SyncData> {
            override fun onResponse(call: Call<SyncData>, response: Response<SyncData>) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    checkExpiryAndProceed(body)
                } else {
                    showActivationRequired()
                }
            }

            override fun onFailure(call: Call<SyncData>, t: Throwable) {
                showError("Network Error: ${t.message}")
            }
        })
    }

    private fun checkExpiryAndProceed(data: SyncData) {
        val expiry = data.expiryDate ?: 0L
        
        if (expiry == 0L) {
            // Unlimited
            completeSync(data)
            return
        }

        // Check real time from WorldTimeAPI
        val timeService = GlobalSync.worldTimeRetrofit().create(WorldTimeService::class.java)
        timeService.getUtcTime().enqueue(object : Callback<WorldTimeResponse> {
            override fun onResponse(call: Call<WorldTimeResponse>, response: Response<WorldTimeResponse>) {
                val networkTimeSeconds = response.body()?.unixTime
                if (response.isSuccessful && networkTimeSeconds != null) {
                    val networkTimeMillis = networkTimeSeconds * 1000
                    if (networkTimeMillis > expiry) {
                        showExpired(expiry)
                    } else {
                        completeSync(data)
                    }
                } else {
                    // Fallback to system time if API fails, but warn or just proceed?
                    // Better to proceed but show a warning if system time is way off.
                    val systemTime = System.currentTimeMillis()
                    if (systemTime > expiry) {
                        showExpired(expiry)
                    } else {
                        completeSync(data)
                    }
                }
            }

            override fun onFailure(call: Call<WorldTimeResponse>, t: Throwable) {
                // Network time failed, use system time as fallback
                val systemTime = System.currentTimeMillis()
                if (systemTime > expiry) {
                    showExpired(expiry)
                } else {
                    completeSync(data)
                }
            }
        })
    }

    private fun completeSync(data: SyncData) {
        val synced = GlobalSync.applySyncedConfig(this, sharedPrefManager, data)
        if (synced) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        } else {
            showError("Invalid account configuration")
        }
    }

    private fun showActivationRequired() {
        binding.progressSync.visibility = View.GONE
        binding.btnRetry.visibility = View.VISIBLE
        binding.txtStatus.text = getString(R.string.activation_status_not_active)
        binding.txtStatus.setTextColor(Color.parseColor("#FF4B2B"))
    }

    private fun showExpired(expiry: Long) {
        binding.progressSync.visibility = View.GONE
        binding.btnRetry.visibility = View.VISIBLE
        val dateStr = ActivationHelper.formatExpiryDate(expiry)
        binding.txtStatus.text = "${getString(R.string.activation_status_expired)}\n($dateStr)"
        binding.txtStatus.setTextColor(Color.parseColor("#FF4B2B"))
    }

    private fun showError(msg: String) {
        binding.progressSync.visibility = View.GONE
        binding.btnRetry.visibility = View.VISIBLE
        binding.txtStatus.text = msg
        binding.txtStatus.setTextColor(Color.YELLOW)
    }
}

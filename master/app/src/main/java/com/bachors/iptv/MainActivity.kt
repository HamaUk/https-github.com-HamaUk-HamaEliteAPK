package com.bachors.iptv

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import com.bachors.iptv.databinding.ActivityMainBinding
import com.bachors.iptv.utils.DeviceSyncCoordinator
import com.bachors.iptv.utils.GlobalSync
import com.bachors.iptv.utils.ActivationHelper
import com.bachors.iptv.utils.SharedPrefManager
import com.bachors.iptv.utils.ThemeHelper
import com.bachors.iptv.utils.SyncData
import com.bachors.iptv.utils.WorldTimeResponse
import com.bachors.iptv.utils.WorldTimeService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : BaseThemedAppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefManager: SharedPrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeHelper.applyMainActivationBackground(binding.root)
        supportActionBar?.hide()

        sharedPrefManager = SharedPrefManager(this)

        binding.txtStatus.visibility = View.GONE
        binding.rowSyncProgress.visibility = View.GONE

        binding.txtDeviceRef.text = getString(R.string.main_device_ref, ActivationHelper.getDeviceCode(this))

        binding.btnStart.setOnClickListener {
            performGlobalSync()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun performGlobalSync() {
        binding.btnStart.visibility = View.GONE
        binding.rowSyncProgress.visibility = View.VISIBLE
        binding.txtStatus.visibility = View.GONE

        DeviceSyncCoordinator.loadEffectivePlaylist(
            this,
            onLoaded = { checkExpiryAndProceed(it) },
            onGlobalMissing = { showActivationRequired() },
            onDedicatedMissing = {
                showError(getString(R.string.sync_assigned_unavailable))
            },
            onPrivateNotLinked = {
                showError(getString(R.string.sync_private_not_linked))
            },
            onFailure = { msg ->
                showError(getString(R.string.sync_error, msg.ifBlank { "—" }))
            },
        )
    }

    private fun checkExpiryAndProceed(data: SyncData) {
        val expiry = data.expiryDate ?: 0L

        if (expiry == 0L) {
            completeSync(data)
            return
        }

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
                    val systemTime = System.currentTimeMillis()
                    if (systemTime > expiry) {
                        showExpired(expiry)
                    } else {
                        completeSync(data)
                    }
                }
            }

            override fun onFailure(call: Call<WorldTimeResponse>, t: Throwable) {
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
            sharedPrefManager.recordSuccessfulSync()
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        } else {
            showError(getString(R.string.sync_invalid))
        }
    }

    private fun showActivationRequired() {
        binding.rowSyncProgress.visibility = View.GONE
        binding.btnStart.visibility = View.VISIBLE
        binding.btnStart.setText(R.string.activation_btn_retry)
        binding.txtStatus.visibility = View.VISIBLE
        binding.txtStatus.text = getString(R.string.main_playlist_unavailable)
        binding.txtStatus.setTextColor(Color.parseColor("#FF4B2B"))
    }

    private fun showExpired(expiry: Long) {
        binding.rowSyncProgress.visibility = View.GONE
        binding.btnStart.visibility = View.VISIBLE
        binding.btnStart.setText(R.string.activation_btn_retry)
        binding.txtStatus.visibility = View.VISIBLE
        binding.txtStatus.text = getString(R.string.main_subscription_ended)
        binding.txtStatus.setTextColor(Color.parseColor("#FF4B2B"))
    }

    private fun showError(msg: String) {
        binding.rowSyncProgress.visibility = View.GONE
        binding.btnStart.visibility = View.VISIBLE
        binding.btnStart.setText(R.string.activation_btn_retry)
        binding.txtStatus.visibility = View.VISIBLE
        binding.txtStatus.text = msg
        binding.txtStatus.setTextColor(Color.parseColor("#FFC107"))
    }
}

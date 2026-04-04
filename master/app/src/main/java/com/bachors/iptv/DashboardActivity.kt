package com.bachors.iptv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bachors.iptv.databinding.ActivityDashboardBinding
import com.bachors.iptv.models.PlaylistData
import com.bachors.iptv.utils.ActivationHelper
import com.bachors.iptv.utils.AppStatus
import com.bachors.iptv.utils.ContinueWatchingStore
import com.bachors.iptv.utils.GlobalSync
import com.bachors.iptv.utils.IptvService
import com.bachors.iptv.utils.SharedPrefManager
import com.bachors.iptv.utils.SyncData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var sharedPrefManager: SharedPrefManager
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityDashboardBinding.inflate(layoutInflater)
            setContentView(binding.root)

            supportActionBar?.hide()
            sharedPrefManager = SharedPrefManager(this)
            binding.tvDashboardVersion.text =
                getString(R.string.dashboard_version_line, BuildConfig.VERSION_NAME)
            binding.tvBrandPrimary.text = getString(R.string.app_name_part_hama)
            binding.tvBrandSecondary.text = getString(R.string.app_name_part_uk)
            setupClock()
            setupDeviceCode()
            setupClickListeners()
            updateCategoryCounts()
            fetchAppStatus()
            refreshContinueWatchingRow()
        } catch (t: Throwable) {
            t.printStackTrace()
            Toast.makeText(this, getString(R.string.dash_startup_error), Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateCategoryCounts()
        refreshContinueWatchingRow()
        fetchAppStatus()
    }

    private fun setupDeviceCode() {
        binding.tvDeviceCode.text = getString(R.string.dashboard_device_code, deviceCodeForDisplay())
    }

    private fun deviceCodeForDisplay(): String {
        val code = ActivationHelper.getDeviceCode(this)
        return if (code.length == 8) {
            "${code.substring(0, 4)} ${code.substring(4)}"
        } else {
            code
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

        binding.cardSports.setOnClickListener {
            startActivity(Intent(this, com.bachors.iptv.sports.SportsActivity::class.java))
        }

        binding.cardQuran.setOnClickListener {
            startActivity(Intent(this, com.bachors.iptv.quran.QuranActivity::class.java))
        }

        binding.cardSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        binding.cardReload.setOnClickListener { performGlobalReload() }

        binding.rowContinueWatching.setOnClickListener {
            startActivity(Intent(this, ContinueWatchingActivity::class.java))
        }
    }

    private fun refreshContinueWatchingRow() {
        val has = ContinueWatchingStore.getAll(this).isNotEmpty()
        binding.rowContinueWatching.visibility = if (has) View.VISIBLE else View.GONE
    }

    private fun fetchAppStatus() {
        val service = GlobalSync.retrofit().create(IptvService::class.java)
        service.getAppStatus().enqueue(object : Callback<AppStatus> {
            override fun onResponse(call: Call<AppStatus>, response: Response<AppStatus>) {
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    binding.appStatusBanner.visibility = View.GONE
                    return
                }
                val state = body.state?.trim()?.lowercase() ?: "ok"
                if (state == "ok" || state.isEmpty()) {
                    binding.appStatusBanner.visibility = View.GONE
                    return
                }
                binding.appStatusBanner.visibility = View.VISIBLE
                val msg = body.message_ku?.takeIf { it.isNotBlank() }
                    ?: body.message?.takeIf { it.isNotBlank() }
                    ?: when (state) {
                        "maintenance" -> "ئێستا چاککردنەوەی سێرڤەر دەستپێکراوە. تکایە دواتر هەوڵبدەرەوە."
                        "degraded" -> "خزمەتگوزاری سێرڤەر کەمکراوە."
                        else -> "دۆخی سێرڤەر: $state"
                    }
                binding.appStatusBanner.text = msg
            }

            override fun onFailure(call: Call<AppStatus>, t: Throwable) {
                binding.appStatusBanner.visibility = View.GONE
            }
        })
    }

    private fun performGlobalReload() {
        binding.cardReload.isEnabled = false
        binding.cardReload.isClickable = false
        binding.reloadProgress.visibility = View.VISIBLE

        val service = GlobalSync.retrofit().create(IptvService::class.java)
        service.getSyncData(ActivationHelper.getDeviceCode(this)).enqueue(object : Callback<SyncData> {
            override fun onResponse(call: Call<SyncData>, response: Response<SyncData>) {
                binding.cardReload.isEnabled = true
                binding.cardReload.isClickable = true
                binding.reloadProgress.visibility = View.GONE
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val synced = GlobalSync.applySyncedConfig(this@DashboardActivity, sharedPrefManager, body)
                    if (synced) {
                        Toast.makeText(this@DashboardActivity, R.string.sync_success, Toast.LENGTH_SHORT).show()
                        updateCategoryCounts()
                    } else {
                        Toast.makeText(this@DashboardActivity, R.string.sync_invalid, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@DashboardActivity, R.string.sync_no_data, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<SyncData>, t: Throwable) {
                binding.cardReload.isEnabled = true
                binding.cardReload.isClickable = true
                binding.reloadProgress.visibility = View.GONE
                Toast.makeText(
                    this@DashboardActivity,
                    getString(R.string.sync_error, t.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun updateCategoryCounts() {
        try {
            val sp = sharedPrefManager
            val rawJson = sp.getSpPlaylist()
            if (rawJson.isNotEmpty() && rawJson != "[]") {
                val listType = object : TypeToken<List<PlaylistData>>() {}.type
                val data: List<PlaylistData> = try {
                    Gson().fromJson(rawJson, listType)
                } catch (_: Exception) {
                    val outer = org.json.JSONArray(rawJson)
                    Gson().fromJson(outer.getJSONArray(0).toString(), listType)
                }
                val totalChannels = data.sumOf { it.channel.toIntOrNull() ?: 0 }
                binding.tvLabelLive.text = if (totalChannels > 0) getString(R.string.dash_live_count, totalChannels) else getString(R.string.dash_live)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

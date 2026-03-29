package com.bachors.iptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Random

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefManager: SharedPrefManager
    private var deviceId: String = ""

    // File picker for M3U/M3U8 files
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { readM3uFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        sharedPrefManager = SharedPrefManager(this)

        // ── Auto-login: skip to Dashboard if already logged in ──
        val alreadyLoggedIn = sharedPrefManager.getSpM3uDirect().isNotEmpty() ||
                              sharedPrefManager.getSpPlaylist().let { it.isNotEmpty() && it != "[]" }
        if (alreadyLoggedIn) {
            runCatching {
                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
                return
            }.onFailure {
                // If auto-resume path is broken on this device, keep user on login instead of app-closing.
                Toast.makeText(this, "Auto-resume failed, opening login screen.", Toast.LENGTH_SHORT).show()
            }
        }
        // ────────────────────────────────────────────────────────

        setupDeviceId()
        setupTabs()
        setupClickListeners()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    private fun setupDeviceId() {
        val prefs = getSharedPreferences("hk_prefs", Context.MODE_PRIVATE)
        deviceId = prefs.getString("hk_device_id", "") ?: ""
        if (deviceId.isEmpty() || deviceId.length != 6) {
            deviceId = (100000 + Random().nextInt(900000)).toString()
            prefs.edit().putString("hk_device_id", deviceId).apply()
        }
        val formatted = "${deviceId.substring(0, 3)} ${deviceId.substring(3)}"
        binding.tvDeviceCode.text = formatted
    }

    private fun setupTabs() {
        binding.btnTabSync.setOnClickListener   { showLayout("sync") }
        binding.btnTabXtream.setOnClickListener { showLayout("xtream") }
        binding.btnTabM3u.setOnClickListener    { showLayout("m3u") }
    }

    private fun showLayout(type: String) {
        binding.layoutSync.visibility   = if (type == "sync")   View.VISIBLE else View.GONE
        binding.layoutXtream.visibility = if (type == "xtream") View.VISIBLE else View.GONE
        binding.layoutM3u.visibility    = if (type == "m3u")    View.VISIBLE else View.GONE
        binding.btnTabSync.alpha   = if (type == "sync")   1.0f else 0.5f
        binding.btnTabXtream.alpha = if (type == "xtream") 1.0f else 0.5f
        binding.btnTabM3u.alpha    = if (type == "m3u")    1.0f else 0.5f
    }

    private fun setupClickListeners() {
        binding.btnRefreshSync.setOnClickListener { performSync() }

        binding.btnLoginXtream.setOnClickListener {
            val server = binding.etServer.text.toString().trim()
            val user   = binding.etUsername.text.toString().trim()
            val pass   = binding.etPassword.text.toString().trim()
            if (server.isNotEmpty() && user.isNotEmpty()) {
                performXtreamLogin(server, user, pass)
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnLoginM3u.setOnClickListener {
            val url = binding.etM3uUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                sharedPrefManager.saveSPString(SharedPrefManager.SP_M3U_DIRECT, url)
                navigateToDashboard()
            } else {
                Toast.makeText(this, "Enter an M3U URL", Toast.LENGTH_SHORT).show()
            }
        }

        // File picker button — opens file picker for .m3u / .m3u8
        binding.btnPickFile.setOnClickListener {
            filePicker.launch("*/*")
        }
    }

    /** Read a picked M3U/M3U8 file from local storage and save its content */
    private fun readM3uFile(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val reader = BufferedReader(InputStreamReader(inputStream))
            val content = reader.readText()
            reader.close()

            if (content.contains("#EXTM3U") || content.contains("#EXTINF")) {
                // Store directly as raw M3U content, prefixed to distinguish from URL
                sharedPrefManager.saveSPString(SharedPrefManager.SP_M3U_DIRECT, "file_content:$content")
                Toast.makeText(this, "File loaded successfully!", Toast.LENGTH_SHORT).show()
                navigateToDashboard()
            } else {
                Toast.makeText(this, "Invalid M3U file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSync() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://hama-elite-sync-default-rtdb.firebaseio.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(IptvService::class.java)
        service.getSyncData(deviceId).enqueue(object : Callback<SyncData> {
            override fun onResponse(call: Call<SyncData>, response: Response<SyncData>) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val synced = applySyncedConfig(body)
                    if (synced) {
                        Toast.makeText(this@MainActivity, "Sync Successful!", Toast.LENGTH_SHORT).show()
                        navigateToDashboard()
                    } else {
                        Toast.makeText(this@MainActivity, "Sync data is invalid", Toast.LENGTH_LONG).show()
                    }
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
        val normalizedServer = normalizeServer(server)
        val baseUrl = if (normalizedServer.endsWith("/")) normalizedServer else "$normalizedServer/"
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val service = retrofit.create(IptvService::class.java)
        service.loginXtream(user, pass).enqueue(object : Callback<XtreamAuthResponse> {
            override fun onResponse(call: Call<XtreamAuthResponse>, response: Response<XtreamAuthResponse>) {
                if (response.isSuccessful && response.body()?.userInfo != null) {
                    val m3uUrl = buildXtreamM3uUrl(normalizedServer, user, pass)
                    sharedPrefManager.saveSPString(SharedPrefManager.SP_M3U_DIRECT, m3uUrl)
                    sharedPrefManager.saveSPString(SharedPrefManager.SP_PLAYLIST, "[]")
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
        return "$cleanServer/get.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}&type=m3u_plus&output=ts"
    }
}
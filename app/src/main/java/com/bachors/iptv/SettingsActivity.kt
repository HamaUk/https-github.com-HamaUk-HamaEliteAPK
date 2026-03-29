package com.bachors.iptv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bachors.iptv.databinding.ActivitySettingsBinding
import com.bachors.iptv.utils.SharedPrefManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPrefManager: SharedPrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()
        sharedPrefManager = SharedPrefManager(this)

        setupUI()
        setupClickListeners()
        showSection("general") // Default section
    }

    private fun setupUI() {
        val prefs = getSharedPreferences("hk_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("hk_device_id", "Unknown")
        
        binding.tvPkg.text = "Package: HAMA UK ELITE"
        binding.tvMac.text = "Code ID: $deviceId"
        binding.tvExpiry.text = "Status: ACTIVE PREMIUM"
        
        // Load Player Preference
        val isNative = sharedPrefManager.getSpString("player_mode") == "native"
        if (isNative) binding.rbNative.isChecked = true else binding.rbIjk.isChecked = true
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // Sidebar Navigation
        binding.btnGeneral.setOnClickListener { showSection("general") }
        binding.btnContent.setOnClickListener { showSection("content") }
        binding.btnAccount.setOnClickListener { showSection("account") }

        // Player Selection
        binding.rgPlayer.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rb_native) "native" else "ijk"
            sharedPrefManager.saveSPString("player_mode", mode)
            Toast.makeText(this, "Player Engine Updated: $mode", Toast.LENGTH_SHORT).show()
        }

        // Content Actions
        binding.btnClearCache.setOnClickListener {
            clearAppCache()
        }

        binding.btnRefreshData.setOnClickListener {
            refreshPlaylistData()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun showSection(section: String) {
        binding.layoutGeneral.visibility = if (section == "general") View.VISIBLE else View.GONE
        binding.layoutContent.visibility = if (section == "content") View.VISIBLE else View.GONE
        binding.layoutAccount.visibility = if (section == "account") View.VISIBLE else View.GONE
        
        // Highlight active button
        binding.btnGeneral.alpha = if (section == "general") 1.0f else 0.6f
        binding.btnContent.alpha = if (section == "content") 1.0f else 0.6f
        binding.btnAccount.alpha = if (section == "account") 1.0f else 0.6f
    }

    private fun clearAppCache() {
        try {
            val dir: File = cacheDir
            dir.deleteRecursively()
            Toast.makeText(this, "All Cache Cleared", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error clearing cache", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshPlaylistData() {
        sharedPrefManager.saveSPString(SharedPrefManager.SP_CHANNELS, "[]")
        Toast.makeText(this, "Data scheduled for refresh", Toast.LENGTH_SHORT).show()
        finish() // Return to dashboard to trigger reload
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout and clear your account details?")
            .setPositiveButton("LOGOUT") { _, _ ->
                performLogout()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun performLogout() {
        val prefs = getSharedPreferences("hk_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("hk_device_id").apply()
        
        sharedPrefManager.saveSPString(SharedPrefManager.SP_PLAYLIST, "")
        sharedPrefManager.saveSPString(SharedPrefManager.SP_CHANNELS, "")
        sharedPrefManager.saveSPString(SharedPrefManager.SP_M3U_DIRECT, "")

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

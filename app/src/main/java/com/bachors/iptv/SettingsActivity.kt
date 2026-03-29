package com.bachors.iptv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bachors.iptv.databinding.ActivitySettingsBinding
import com.bachors.iptv.utils.SharedPrefManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
    }

    private fun setupUI() {
        val prefs = getSharedPreferences("hk_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("hk_device_id", "Unknown")
        
        binding.tvPkg.text = "Package: HAMA UK ELITE"
        binding.tvMac.text = "Code ID: $deviceId"
        binding.tvExpiry.text = "Status: ACTIVE (v15.1.0)"
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnGeneral.setOnClickListener { showToast("General Settings Loaded") }
        binding.btnContent.setOnClickListener { showToast("Content Cache Cleared") }
        binding.btnAccount.setOnClickListener { showToast("Showing Account Details") }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
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
        // Clear stored credentials
        val prefs = getSharedPreferences("hk_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("hk_device_id").apply()
        
        sharedPrefManager.saveSPString(SharedPrefManager.SP_PLAYLIST, "")
        sharedPrefManager.saveSPString(SharedPrefManager.SP_CHANNELS, "")

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

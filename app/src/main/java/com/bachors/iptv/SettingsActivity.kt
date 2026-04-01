package com.bachors.iptv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.ui.AspectRatioFrameLayout
import com.bachors.iptv.databinding.ActivitySettingsBinding
import com.bachors.iptv.utils.SharedPrefManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
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
        showSection("general")
    }

    private fun setupUI() {
        val ver = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "—"

        binding.tvPkg.text = "پاکێج: HAMA UK ELITE"
        binding.tvMac.text = getString(R.string.settings_version_line, ver)
        binding.tvExpiry.text = "دۆخ: پڕیمیۆمی چالاک"

        val savedResize = sharedPrefManager.getResizeMode()
        when (savedResize) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT.toString() -> binding.rbFit.isChecked = true
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM.toString() -> binding.rbZoom.isChecked = true
            AspectRatioFrameLayout.RESIZE_MODE_FILL.toString() -> binding.rbFill.isChecked = true
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH.toString() -> binding.rbFixedWidth.isChecked = true
            else -> binding.rbFit.isChecked = true
        }

        binding.switchAutoplay.isChecked = sharedPrefManager.getAutoplay()

        when (sharedPrefManager.getBufferSize()) {
            "low" -> binding.rbBufferLow.isChecked = true
            "high" -> binding.rbBufferHigh.isChecked = true
            else -> binding.rbBufferMedium.isChecked = true
        }

        binding.switchHwAccel.isChecked = sharedPrefManager.getHwAccel()

        updateActivePlaylistLabel()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnGeneral.setOnClickListener { showSection("general") }
        binding.btnPlayerSection.setOnClickListener { showSection("player") }
        binding.btnContent.setOnClickListener { showSection("content") }
        binding.btnAccount.setOnClickListener { showSection("account") }

        binding.rgResizeMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rb_fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                R.id.rb_zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                R.id.rb_fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                R.id.rb_fixed_width -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            sharedPrefManager.saveSPString(SharedPrefManager.SP_RESIZE_MODE, mode.toString())
        }

        binding.switchAutoplay.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefManager.saveSPBoolean(SharedPrefManager.SP_AUTOPLAY, isChecked)
        }

        binding.rgBuffer.setOnCheckedChangeListener { _, checkedId ->
            val size = when (checkedId) {
                R.id.rb_buffer_low -> "low"
                R.id.rb_buffer_high -> "high"
                else -> "medium"
            }
            sharedPrefManager.saveSPString(SharedPrefManager.SP_BUFFER_SIZE, size)
        }

        binding.switchHwAccel.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefManager.saveSPBoolean(SharedPrefManager.SP_HW_ACCEL, isChecked)
        }

        binding.btnClearCache.setOnClickListener { clearAppCache() }
        binding.btnRefreshData.setOnClickListener { refreshPlaylistData() }
        binding.btnLogout.setOnClickListener { showLogoutConfirmation() }

        // Playlist management
        binding.btnSavePlaylist.setOnClickListener { showSavePlaylistDialog() }
        binding.btnLoadPlaylist.setOnClickListener { showLoadPlaylistDialog() }
        binding.btnDeletePlaylist.setOnClickListener { showDeletePlaylistDialog() }
    }

    private fun showSection(section: String) {
        binding.layoutGeneral.visibility = if (section == "general") View.VISIBLE else View.GONE
        binding.layoutPlayer.visibility = if (section == "player") View.VISIBLE else View.GONE
        binding.layoutContent.visibility = if (section == "content") View.VISIBLE else View.GONE
        binding.layoutAccount.visibility = if (section == "account") View.VISIBLE else View.GONE

        binding.btnGeneral.alpha = if (section == "general") 1.0f else 0.6f
        binding.btnPlayerSection.alpha = if (section == "player") 1.0f else 0.6f
        binding.btnContent.alpha = if (section == "content") 1.0f else 0.6f
        binding.btnAccount.alpha = if (section == "account") 1.0f else 0.6f
    }

    // ── Playlist Management ─────────────────────────────────
    private fun getPlaylistsJson(): JSONObject {
        return try { JSONObject(sharedPrefManager.getSavedPlaylists()) } catch (_: Exception) { JSONObject() }
    }

    private fun updateActivePlaylistLabel() {
        val name = sharedPrefManager.getActivePlaylistName()
        binding.tvActivePlaylist.text = name.ifEmpty { "بنەڕەت" }
    }

    private fun showSavePlaylistDialog() {
        val input = EditText(this).apply {
            hint = "ناوی پلەی لیست"
            setTextColor(resources.getColor(android.R.color.white, null))
            setHintTextColor(resources.getColor(android.R.color.darker_gray, null))
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .setTitle("پاشەکەوتکردنی پلەی لیستی ئێستا")
            .setView(input)
            .setPositiveButton("پاشەکەوت") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "ناو نابێت بەتاڵ بێت", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val playlists = getPlaylistsJson()
                val data = JSONObject()
                data.put("playlist", sharedPrefManager.getSpPlaylist())
                data.put("channels", sharedPrefManager.getSpChannels())
                data.put("m3u", sharedPrefManager.getSpM3uDirect())
                playlists.put(name, data)
                sharedPrefManager.saveSPString(SharedPrefManager.SP_SAVED_PLAYLISTS, playlists.toString())
                sharedPrefManager.saveSPString(SharedPrefManager.SP_ACTIVE_PLAYLIST_NAME, name)
                updateActivePlaylistLabel()
                Toast.makeText(this, "پلەی لیستی '$name' پاشەکەوت کرا", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("هەڵوەشاندنەوە", null)
            .show()
    }

    private fun showLoadPlaylistDialog() {
        val playlists = getPlaylistsJson()
        val names = playlists.keys().asSequence().toList()
        if (names.isEmpty()) {
            Toast.makeText(this, "هیچ پلەی لیستێکی پاشەکەوتکراو نییە", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .setTitle("گۆڕینی پلەی لیست")
            .setItems(names.toTypedArray()) { _, which ->
                val name = names[which]
                try {
                    val data = playlists.getJSONObject(name)
                    sharedPrefManager.saveSPString(SharedPrefManager.SP_PLAYLIST, data.optString("playlist", "[]"))
                    sharedPrefManager.saveSPString(SharedPrefManager.SP_CHANNELS, data.optString("channels", "[]"))
                    sharedPrefManager.saveSPString(SharedPrefManager.SP_M3U_DIRECT, data.optString("m3u", ""))
                    sharedPrefManager.saveSPString(SharedPrefManager.SP_ACTIVE_PLAYLIST_NAME, name)
                    updateActivePlaylistLabel()
                    Toast.makeText(this, "گۆڕدرا بۆ '$name'", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "بارکردنی پلەی لیست شکستی هێنا", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showDeletePlaylistDialog() {
        val playlists = getPlaylistsJson()
        val names = playlists.keys().asSequence().toList()
        if (names.isEmpty()) {
            Toast.makeText(this, "هیچ پلەی لیستێکی پاشەکەوتکراو نییە", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .setTitle("سڕینەوەی پلەی لیست")
            .setItems(names.toTypedArray()) { _, which ->
                val name = names[which]
                MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
                    .setTitle("سڕینەوەی '$name'؟")
                    .setMessage("ئەم کارە ناگەڕێتەوە.")
                    .setPositiveButton("بسڕەوە") { _, _ ->
                        playlists.remove(name)
                        sharedPrefManager.saveSPString(SharedPrefManager.SP_SAVED_PLAYLISTS, playlists.toString())
                        if (sharedPrefManager.getActivePlaylistName() == name) {
                            sharedPrefManager.saveSPString(SharedPrefManager.SP_ACTIVE_PLAYLIST_NAME, "")
                            updateActivePlaylistLabel()
                        }
                        Toast.makeText(this, "'$name' سڕایەوە", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("هەڵوەشاندنەوە", null)
                    .show()
            }
            .show()
    }

    // ── Cache / Data ────────────────────────────────────────
    private fun clearAppCache() {
        try {
            val dir: File = cacheDir
            dir.deleteRecursively()
            Toast.makeText(this, "هەموو کاش پاککرایەوە", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "هەڵە لە پاککردنەوەی کاش", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshPlaylistData() {
        sharedPrefManager.saveSPString(SharedPrefManager.SP_CHANNELS, "[]")
        Toast.makeText(this, "داتا بۆ نوێکردنەوە دیاریکرا", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .setTitle("چوونەدەرەوە")
            .setMessage("دڵنیای دەتەوێت بچیتەدەرەوە و زانیاری هەژمارەکەت بسڕیتەوە؟")
            .setPositiveButton("بچۆ دەرەوە") { _, _ ->
                performLogout()
            }
            .setNegativeButton("هەڵوەشاندنەوە", null)
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

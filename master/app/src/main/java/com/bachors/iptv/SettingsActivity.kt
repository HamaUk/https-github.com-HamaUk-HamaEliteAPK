package com.bachors.iptv

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.media3.ui.AspectRatioFrameLayout
import com.bachors.iptv.databinding.ActivitySettingsBinding
import com.bachors.iptv.utils.ActivationHelper
import com.bachors.iptv.utils.AppLocaleHelper
import com.bachors.iptv.utils.PlayerLauncher
import com.bachors.iptv.utils.SharedPrefManager
import com.bachors.iptv.utils.ThemeHelper
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : BaseThemedAppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPrefManager: SharedPrefManager

    private var suppressStreamQuality = false
    private var suppressLanguageRadio = false
    private var suppressThemeRadio = false
    private var suppressPrivatePlaylistSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeHelper.applyPremiumHeroBackground(binding.root)

        supportActionBar?.hide()
        sharedPrefManager = SharedPrefManager(this)

        setupUI()
        setupClickListeners()
        showSection("general")
    }

    override fun onResume() {
        super.onResume()
        ThemeHelper.applyPremiumHeroBackground(binding.root)
        updateLastSyncDisplay()
        refreshAccountPanel()
    }

    private fun setupUI() {
        val ver = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "—"

        binding.tvPkg.text = getString(R.string.settings_package_line, packageName)
        binding.tvVersion.text = getString(R.string.settings_version_line, ver)
        refreshAccountPanel()

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

        when (sharedPrefManager.getSpString(SharedPrefManager.SP_PLAYER_ENGINE).lowercase()) {
            PlayerLauncher.ENGINE_EXO_CINEMA -> binding.rbPlayerExoCinema.isChecked = true
            PlayerLauncher.ENGINE_EXO_ARENA -> binding.rbPlayerExoArena.isChecked = true
            else -> binding.rbPlayerExo.isChecked = true
        }

        suppressStreamQuality = true
        when (sharedPrefManager.getSpInt(SharedPrefManager.SP_VIDEO_QUALITY_PRESET, 0)) {
            1 -> binding.rbStream720.isChecked = true
            2 -> binding.rbStream1080.isChecked = true
            3 -> binding.rbStream4k.isChecked = true
            else -> binding.rbStreamAuto.isChecked = true
        }
        suppressStreamQuality = false

        bindLanguageRadiosFromPrefs()
        bindThemeRadiosFromPrefs()

        binding.tvDeviceCodeValue.text = ActivationHelper.getDeviceCode(this)
        suppressPrivatePlaylistSwitch = true
        binding.switchPrivatePlaylist.isChecked =
            sharedPrefManager.getSpBoolean(SharedPrefManager.SP_USE_PRIVATE_PLAYLIST, false)
        suppressPrivatePlaylistSwitch = false

        updateActivePlaylistLabel()
        updateLastSyncDisplay()
    }

    private fun refreshAccountPanel() {
        val expiry = sharedPrefManager.getSpLong(SharedPrefManager.SP_EXPIRY_DATE, 0L)
        val now = System.currentTimeMillis()
        binding.tvExpiry.text = when {
            expiry <= 0L -> getString(R.string.settings_expiry_unlimited)
            else -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                getString(R.string.settings_expiry_line, sdf.format(Date(expiry)))
            }
        }
        binding.tvAccountStatus.text = when {
            expiry > 0L && expiry < now -> getString(R.string.activation_status_expired)
            else -> getString(R.string.settings_account_public)
        }
    }

    private fun bindLanguageRadiosFromPrefs() {
        suppressLanguageRadio = true
        when (sharedPrefManager.getAppLanguageKey()) {
            SharedPrefManager.LANGUAGE_KMR -> binding.rbLangBadini.isChecked = true
            SharedPrefManager.LANGUAGE_AR -> binding.rbLangAr.isChecked = true
            SharedPrefManager.LANGUAGE_EN -> binding.rbLangEn.isChecked = true
            else -> binding.rbLangSorani.isChecked = true
        }
        suppressLanguageRadio = false
    }

    private fun bindThemeRadiosFromPrefs() {
        suppressThemeRadio = true
        when (sharedPrefManager.getThemeMode()) {
            ThemeHelper.THEME_AMOLED -> binding.rbThemeAmoled.isChecked = true
            ThemeHelper.THEME_LIGHT -> binding.rbThemeLight.isChecked = true
            else -> binding.rbThemeDark.isChecked = true
        }
        suppressThemeRadio = false
    }

    private fun updateLastSyncDisplay() {
        val ms = sharedPrefManager.getSpLong(SharedPrefManager.SP_LAST_SYNC_SUCCESS_AT, 0L)
        binding.tvLastSyncValue.text = if (ms <= 0L) {
            getString(R.string.settings_last_sync_never)
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(ms))
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnGeneral.setOnClickListener { showSection("general") }
        binding.btnPlayerSection.setOnClickListener { showSection("player") }
        binding.btnAccount.setOnClickListener { showSection("account") }
        binding.btnLanguage.setOnClickListener { showSection("language") }
        binding.btnAppearance.setOnClickListener { showSection("appearance") }
        binding.btnLastSyncSection.setOnClickListener { showSection("last_sync") }

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

        binding.rgStreamQuality.setOnCheckedChangeListener { _, checkedId ->
            if (suppressStreamQuality) return@setOnCheckedChangeListener
            val preset = when (checkedId) {
                R.id.rb_stream_720 -> 1
                R.id.rb_stream_1080 -> 2
                R.id.rb_stream_4k -> 3
                else -> 0
            }
            sharedPrefManager.saveSPInt(SharedPrefManager.SP_VIDEO_QUALITY_PRESET, preset)
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

        binding.rgPlayerEngine.setOnCheckedChangeListener { _, checkedId ->
            val engine = when (checkedId) {
                R.id.rb_player_exo_cinema -> PlayerLauncher.ENGINE_EXO_CINEMA
                R.id.rb_player_exo_arena -> PlayerLauncher.ENGINE_EXO_ARENA
                else -> PlayerLauncher.ENGINE_EXO
            }
            sharedPrefManager.saveSPString(SharedPrefManager.SP_PLAYER_ENGINE, engine)
        }

        binding.rgAppLanguage.setOnCheckedChangeListener { _, checkedId ->
            if (suppressLanguageRadio) return@setOnCheckedChangeListener
            val key = when (checkedId) {
                R.id.rb_lang_badini -> SharedPrefManager.LANGUAGE_KMR
                R.id.rb_lang_ar -> SharedPrefManager.LANGUAGE_AR
                R.id.rb_lang_en -> SharedPrefManager.LANGUAGE_EN
                else -> SharedPrefManager.LANGUAGE_CKB
            }
            if (key == sharedPrefManager.getAppLanguageKey()) return@setOnCheckedChangeListener
            sharedPrefManager.saveAppLanguageKey(key)
            AppLocaleHelper.applySavedApplicationLocales(this)
            recreate()
        }

        binding.rgThemeMode.setOnCheckedChangeListener { _, checkedId ->
            if (suppressThemeRadio) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.rb_theme_amoled -> ThemeHelper.THEME_AMOLED
                R.id.rb_theme_light -> ThemeHelper.THEME_LIGHT
                else -> ThemeHelper.THEME_DARK
            }
            if (mode == sharedPrefManager.getThemeMode()) return@setOnCheckedChangeListener
            sharedPrefManager.saveThemeMode(mode)
            ThemeHelper.applyDefaultNightMode(this)
            recreate()
        }

        binding.btnClearCache.setOnClickListener { clearAppCache() }
        binding.btnRefreshData.setOnClickListener { refreshPlaylistData() }

        binding.btnCopyDeviceCode.setOnClickListener {
            val code = ActivationHelper.getDeviceCode(this)
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("device_code", code))
            Toast.makeText(this, R.string.settings_device_id_copied, Toast.LENGTH_SHORT).show()
        }

        binding.switchPrivatePlaylist.setOnCheckedChangeListener { _, isChecked ->
            if (suppressPrivatePlaylistSwitch) return@setOnCheckedChangeListener
            sharedPrefManager.saveSPBoolean(SharedPrefManager.SP_USE_PRIVATE_PLAYLIST, isChecked)
            Toast.makeText(this, R.string.settings_playlist_source_saved, Toast.LENGTH_SHORT).show()
        }

        binding.btnSavePlaylist.setOnClickListener { showSavePlaylistDialog() }
        binding.btnLoadPlaylist.setOnClickListener { showLoadPlaylistDialog() }
        binding.btnDeletePlaylist.setOnClickListener { showDeletePlaylistDialog() }
    }

    private fun showSection(section: String) {
        binding.layoutGeneral.visibility = if (section == "general") View.VISIBLE else View.GONE
        binding.layoutPlayer.visibility = if (section == "player") View.VISIBLE else View.GONE
        binding.layoutAccount.visibility = if (section == "account") View.VISIBLE else View.GONE
        binding.layoutLanguage.visibility = if (section == "language") View.VISIBLE else View.GONE
        binding.layoutAppearance.visibility = if (section == "appearance") View.VISIBLE else View.GONE
        binding.layoutLastSync.visibility = if (section == "last_sync") View.VISIBLE else View.GONE

        binding.btnGeneral.alpha = if (section == "general") 1.0f else 0.6f
        binding.btnPlayerSection.alpha = if (section == "player") 1.0f else 0.6f
        binding.btnAccount.alpha = if (section == "account") 1.0f else 0.6f
        binding.btnLanguage.alpha = if (section == "language") 1.0f else 0.6f
        binding.btnAppearance.alpha = if (section == "appearance") 1.0f else 0.6f
        binding.btnLastSyncSection.alpha = if (section == "last_sync") 1.0f else 0.6f

        if (section == "last_sync") updateLastSyncDisplay()
    }

    private fun getPlaylistsJson(): JSONObject {
        return try { JSONObject(sharedPrefManager.getSavedPlaylists()) } catch (_: Exception) { JSONObject() }
    }

    private fun updateActivePlaylistLabel() {
        val name = sharedPrefManager.getActivePlaylistName()
        binding.tvActivePlaylist.text = name.ifEmpty { getString(R.string.settings_active_default) }
    }

    private fun showSavePlaylistDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.dialog_save_playlist_hint)
            val onSurface = MaterialColors.getColor(this@SettingsActivity, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE)
            val onSurfaceVar = MaterialColors.getColor(this@SettingsActivity, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY)
            setTextColor(onSurface)
            setHintTextColor(onSurfaceVar)
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this, ThemeHelper.getMaterialAlertDialogThemeResId(this))
            .setTitle(getString(R.string.dialog_save_playlist_title))
            .setView(input)
            .setPositiveButton(getString(R.string.dialog_save_btn)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.dialog_name_empty), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, getString(R.string.dialog_playlist_saved, name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun showLoadPlaylistDialog() {
        val playlists = getPlaylistsJson()
        val names = playlists.keys().asSequence().toList()
        if (names.isEmpty()) {
            Toast.makeText(this, getString(R.string.dialog_no_saved), Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this, ThemeHelper.getMaterialAlertDialogThemeResId(this))
            .setTitle(getString(R.string.dialog_switch_title))
            .setItems(names.toTypedArray()) { _, which ->
                val name = names[which]
                try {
                    val data = playlists.getJSONObject(name)
                    sharedPrefManager.saveSPString(SharedPrefManager.SP_PLAYLIST, data.optString("playlist", "[]"))
                    sharedPrefManager.saveSPString(SharedPrefManager.SP_CHANNELS, data.optString("channels", "[]"))
                    sharedPrefManager.saveSPString(SharedPrefManager.SP_M3U_DIRECT, data.optString("m3u", ""))
                    sharedPrefManager.saveSPString(SharedPrefManager.SP_ACTIVE_PLAYLIST_NAME, name)
                    updateActivePlaylistLabel()
                    Toast.makeText(this, getString(R.string.dialog_switched_to, name), Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(this, getString(R.string.dialog_load_failed), Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showDeletePlaylistDialog() {
        val playlists = getPlaylistsJson()
        val names = playlists.keys().asSequence().toList()
        if (names.isEmpty()) {
            Toast.makeText(this, getString(R.string.dialog_no_saved), Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this, ThemeHelper.getMaterialAlertDialogThemeResId(this))
            .setTitle(getString(R.string.dialog_delete_title))
            .setItems(names.toTypedArray()) { _, which ->
                val name = names[which]
                MaterialAlertDialogBuilder(this, ThemeHelper.getMaterialAlertDialogThemeResId(this))
                    .setTitle(getString(R.string.dialog_delete_confirm, name))
                    .setMessage(getString(R.string.dialog_delete_warn))
                    .setPositiveButton(getString(R.string.dialog_delete_btn)) { _, _ ->
                        playlists.remove(name)
                        sharedPrefManager.saveSPString(SharedPrefManager.SP_SAVED_PLAYLISTS, playlists.toString())
                        if (sharedPrefManager.getActivePlaylistName() == name) {
                            sharedPrefManager.saveSPString(SharedPrefManager.SP_ACTIVE_PLAYLIST_NAME, "")
                            updateActivePlaylistLabel()
                        }
                        Toast.makeText(this, getString(R.string.dialog_deleted, name), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(getString(R.string.dialog_cancel), null)
                    .show()
            }
            .show()
    }

    private fun clearAppCache() {
        try {
            val dir: File = cacheDir
            dir.deleteRecursively()
            Toast.makeText(this, getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.cache_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshPlaylistData() {
        sharedPrefManager.saveSPString(SharedPrefManager.SP_CHANNELS, "[]")
        Toast.makeText(this, getString(R.string.data_refresh_scheduled), Toast.LENGTH_SHORT).show()
        finish()
    }
}

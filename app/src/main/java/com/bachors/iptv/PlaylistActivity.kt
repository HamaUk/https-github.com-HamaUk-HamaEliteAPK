package com.bachors.iptv

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bachors.iptv.adapters.ChannelsAdapter
import com.bachors.iptv.adapters.PlaylistAdapter
import com.bachors.iptv.databinding.ActivityPlaylistBinding
import com.bachors.iptv.models.ChannelsData
import com.bachors.iptv.models.PlaylistData
import com.bachors.iptv.utils.HttpHandler
import com.bachors.iptv.utils.RecyclerTouchListener
import com.bachors.iptv.utils.SharedPrefManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlaylistActivity : AppCompatActivity() {

    companion object {
        private const val EXT_M3U   = "#EXTM3U"
        private const val EXT_INF   = "#EXTINF"
        private const val EXT_HTTP  = "http://"
        private const val EXT_HTTPS = "https://"
    }

    private lateinit var binding: ActivityPlaylistBinding
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var loading: AlertDialog
    private lateinit var categoryAdapter: PlaylistAdapter
    private lateinit var channelAdapter: ChannelsAdapter

    // All groups parsed from M3U — group title → channel list
    private val groupMap = mutableMapOf<String, MutableList<ChannelsData>>()
    private val groupNames = mutableListOf<String>()

    // Currently displayed channels (can be filtered)
    private var currentGroupChannels = mutableListOf<ChannelsData>()
    private var currentType = "live" // live | vod | series

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullscreen()
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        sharedPrefManager = SharedPrefManager(this)
        currentType = intent.getStringExtra("type") ?: "live"

        setupLoadingDialog()
        setupUI()
        loadData()
    }

    // ── Fullscreen / TV ────────────────────────────────────
    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.hide(WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onResume() { super.onResume(); goFullscreen() }

    // ── Loading dialog ─────────────────────────────────────
    private fun setupLoadingDialog() {
        loading = MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .setCancelable(false)
            .setMessage("Loading channels...")
            .create()
    }

    // ── UI Setup ───────────────────────────────────────────
    private fun setupUI() {
        // Category sidebar
        categoryAdapter = PlaylistAdapter(this)
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(this@PlaylistActivity)
            adapter = categoryAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }

        channelAdapter = ChannelsAdapter(this)
        binding.rvChannels.apply {
            layoutManager = LinearLayoutManager(this@PlaylistActivity)
            adapter = channelAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }

        binding.tvHeaderTitle.text = when (currentType) {
            "vod" -> "MOVIES"
            "series" -> "SERIES"
            else -> "LIVE TV"
        }

        try {
            binding.btnBack.setOnClickListener { finish() }
        } catch (_: Exception) { }

        // Search — filter channels by name
        binding.etSearchCategories.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChannels(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Category click
        binding.rvCategories.addOnItemTouchListener(
            RecyclerTouchListener(this, binding.rvCategories, object : RecyclerTouchListener.ClickListener {
                override fun onClick(view: View, position: Int) {
                    showGroup(groupNames.getOrNull(position) ?: return)
                }
                override fun onLongClick(view: View, position: Int) {}
            })
        )

        // Channel click → launch PlayerActivity
        binding.rvChannels.addOnItemTouchListener(
            RecyclerTouchListener(this, binding.rvChannels, object : RecyclerTouchListener.ClickListener {
                override fun onClick(view: View, position: Int) {
                    val ch = channelAdapter.getItem(position)
                    // Save all current channels for player sidebar navigation
                    val gson = Gson()
                    val allCurrent = currentGroupChannels
                    sharedPrefManager.saveSPString(
                        SharedPrefManager.SP_CHANNELS,
                        gson.toJson(allCurrent)
                    )
                    val intent = Intent(this@PlaylistActivity, PlayerActivity::class.java)
                    intent.putExtra("name", ch.name)
                    intent.putExtra("url", ch.url)
                    startActivity(intent)
                }
                override fun onLongClick(view: View, position: Int) {}
            })
        )
    }

    // ── Data Loading ────────────────────────────────────────
    private fun loadData() {
        val directData = sharedPrefManager.getSpM3uDirect()
        if (directData.isNotEmpty()) {
            if (directData.startsWith("file_content:")) {
                // Local file content — parse directly without network
                val content = directData.removePrefix("file_content:")
                parseAndDisplay(content)
            } else {
                // Remote M3U URL — fetch it
                loading.show()
                Thread {
                    val result = HttpHandler().makeServiceCall(directData)
                    runOnUiThread {
                        loading.dismiss()
                        if (result != null) {
                            parseAndDisplay(result.replace("#EXTVLCOPT:(.*)".toRegex(), ""))
                        } else {
                            Toast.makeText(this, "Failed to load playlist", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
        } else {
            // Xtream / JSON playlist mode
            jsonToGroups()
        }
    }

    // ── M3U Parsing ────────────────────────────────────────
    private fun parseAndDisplay(content: String) {
        groupMap.clear()
        groupNames.clear()

        val lines = content.lines()
        var currentName  = ""
        var currentLogo  = ""
        var currentGroup = "General"

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXTINF") -> {
                    // Parse name, logo, group-title from the EXTINF line
                    currentName  = ""
                    currentLogo  = ""
                    currentGroup = extractAttr(trimmed, "group-title") ?: "General"
                    currentLogo  = extractAttr(trimmed, "tvg-logo") ?: ""
                    // Channel display name is after the last comma
                    val commaIdx = trimmed.lastIndexOf(',')
                    if (commaIdx >= 0) currentName = trimmed.substring(commaIdx + 1).trim()
                }
                trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("rtsp://") || trimmed.startsWith("rtmp://") -> {
                    if (currentName.isNotEmpty()) {
                        val detectedType = detectTypeFromUrl(trimmed)
                        val channel = ChannelsData(
                            name = currentName,
                            logo = currentLogo,
                            url  = trimmed
                        )
                        // Key = "type|group" so we can filter by type accurately
                        val key = "$detectedType|$currentGroup"
                        groupMap.getOrPut(key) { mutableListOf() }.add(channel)
                        currentName = ""
                    }
                }
            }
        }

        // Filter by type: only show groups whose key starts with the selected type
        // Keys are "type|group_name" — strip prefix for display
        val matchingKeys = when (currentType) {
            "vod"    -> groupMap.keys.filter { it.startsWith("vod|") }
            "series" -> groupMap.keys.filter { it.startsWith("series|") }
            else     -> groupMap.keys.filter { it.startsWith("live|") }
        }

        // If no type-specific content found, show everything
        val keysToShow = matchingKeys.ifEmpty { groupMap.keys.toList() }

        if (matchingKeys.isEmpty() && currentType != "live") {
            Toast.makeText(this,
                "No ${currentType.uppercase()} content detected in this playlist.",
                Toast.LENGTH_LONG).show()
        }

        // Display names = strip "type|" prefix
        groupNames.clear()
        groupNames.addAll(keysToShow.map { it.substringAfter("|") }.distinct().sorted())

        // Build a clean display map: display_name -> channels
        val displayMap = mutableMapOf<String, MutableList<ChannelsData>>()
        for (key in keysToShow) {
            val displayName = key.substringAfter("|")
            displayMap.getOrPut(displayName) { mutableListOf() }
                .addAll(groupMap[key] ?: emptyList())
        }

        // Populate sidebar
        categoryAdapter.clear()
        val groupData = groupNames.map { name ->
            PlaylistData(title = name, link = "", channel = (displayMap[name]?.size ?: 0).toString())
        }
        categoryAdapter.addAll(groupData)

        // Show first group
        if (groupNames.isNotEmpty()) {
            showGroup(groupNames.first(), displayMap)
        }
    }

    private fun extractAttr(line: String, attr: String): String? {
        val pattern = Regex("""$attr="([^"]*)"""")
        return pattern.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
    }

    // Detect type from URL — reliable for both Xtream Codes and direct M3U
    private fun detectTypeFromUrl(url: String): String {
        val lower = url.lowercase()
        return when {
            // Xtream Codes path convention
            lower.contains("/movie/")  || lower.contains("/movies/")  -> "vod"
            lower.contains("/series/") || lower.contains("/episode/") -> "series"
            lower.contains("/live/")   || lower.contains("/stream/")  -> "live"
            // File extension convention
            lower.endsWith(".mp4") || lower.endsWith(".mkv") ||
            lower.endsWith(".avi") || lower.endsWith(".mov") ||
            lower.endsWith(".wmv") || lower.endsWith(".flv") ||
            lower.endsWith(".ts")  || lower.endsWith(".m4v")         -> "vod"
            lower.endsWith(".m3u8")                                   -> "live"
            else                                                       -> "live" // default
        }
    }

    // ── Group Display ───────────────────────────────────────
    private fun showGroup(name: String, source: Map<String, MutableList<ChannelsData>> = groupMap) {
        val channels = source[name] ?: groupMap[name] ?: return
        currentGroupChannels = channels.toMutableList()
        binding.tvHeaderTitle.text = name.uppercase()
        binding.etSearchCategories.setText("")
        channelAdapter.clear()
        channelAdapter.addAll(currentGroupChannels)
    }

    // ── Search filtering ────────────────────────────────────
    private fun filterChannels(query: String) {
        if (query.isEmpty()) {
            channelAdapter.clear()
            channelAdapter.addAll(currentGroupChannels)
        } else {
            val filtered = currentGroupChannels.filter {
                it.name.contains(query, ignoreCase = true)
            }
            channelAdapter.clear()
            channelAdapter.addAll(filtered)
        }
    }

    // ── Xtream / JSON fallback ──────────────────────────────
    private fun jsonToGroups() {
        try {
            val rawJson = sharedPrefManager.getSpPlaylist()
            if (rawJson.isEmpty() || rawJson == "[]") {
                Toast.makeText(this, "No playlist. Please login first.", Toast.LENGTH_LONG).show()
                return
            }
            val gson = Gson()
            val listType = object : TypeToken<List<PlaylistData>>() {}.type
            val data: List<PlaylistData> = try {
                gson.fromJson(rawJson, listType)
            } catch (e: Exception) {
                val outer = org.json.JSONArray(rawJson)
                gson.fromJson(outer.getJSONArray(0).toString(), listType)
            }

            val filtered = data.filter { it.title.isNotEmpty() }
            groupNames.clear()
            groupNames.addAll(filtered.map { it.title })

            categoryAdapter.clear()
            categoryAdapter.addAll(filtered)

            // Load first category's channels via HTTP
            if (filtered.isNotEmpty()) {
                val first = filtered.first()
                binding.tvHeaderTitle.text = first.title.uppercase()
                loadXtreamChannels(first.link)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading playlist", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadXtreamChannels(url: String?) {
        if (url.isNullOrEmpty()) return
        loading.show()
        Thread {
            val result = HttpHandler().makeServiceCall(url)
            runOnUiThread {
                loading.dismiss()
                if (result != null) {
                    try {
                        val cleaned = result.replace("#EXTVLCOPT:(.*)".toRegex(), "")
                        parseAndDisplay(cleaned)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Failed to parse channels", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }
}
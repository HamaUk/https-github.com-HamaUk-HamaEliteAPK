package com.bachors.iptv

import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.bachors.iptv.utils.SharedPrefManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.NumberFormat

enum class SortMode { DEFAULT, NAME_AZ, NAME_ZA }

class PlaylistActivity : AppCompatActivity() {

    companion object {
        private const val PROGRESS_UPDATE_INTERVAL = 200L
    }

    private lateinit var binding: ActivityPlaylistBinding
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var categoryAdapter: PlaylistAdapter
    private lateinit var channelAdapter: ChannelsAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val numberFormat = NumberFormat.getNumberInstance()

    private val groupMap = mutableMapOf<String, MutableList<ChannelsData>>()
    private val displayGroupMap = mutableMapOf<String, MutableList<ChannelsData>>()
    private val groupNames = mutableListOf<String>()

    private var currentGroupChannels = mutableListOf<ChannelsData>()
    private var currentType = "live"
    private var currentSortMode = SortMode.DEFAULT
    private var lastProgressUpdate = 0L

    // ── Loading overlay views ────────────────────────────────
    private lateinit var loadingOverlay: View
    private lateinit var loadingProgressFill: View
    private lateinit var loadingPercent: android.widget.TextView
    private lateinit var loadingStatus: android.widget.TextView
    private lateinit var loadingTitle: android.widget.TextView
    private lateinit var loadingChannelCount: android.widget.TextView
    private lateinit var loadingIcon: android.widget.ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullscreen()
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        sharedPrefManager = SharedPrefManager(this)
        currentType = intent.getStringExtra("type") ?: "live"

        bindLoadingOverlay()
        setupUI()
        loadData()
    }

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.hide(WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onResume() { super.onResume(); goFullscreen() }

    // ════════════════════════════════════════════════════════
    //  LOADING OVERLAY
    // ════════════════════════════════════════════════════════
    private fun bindLoadingOverlay() {
        loadingOverlay = binding.loadingOverlay
        loadingProgressFill = binding.loadingProgressFill
        loadingPercent = binding.loadingPercent
        loadingStatus = binding.loadingStatus
        loadingTitle = binding.loadingTitle
        loadingChannelCount = binding.loadingChannelCount
        loadingIcon = binding.loadingIcon
    }

    private fun showLoadingOverlay(title: String) {
        loadingTitle.text = title
        loadingStatus.text = "پەیوەندی بە سێرڤەرەوە..."
        loadingPercent.text = "0%"
        loadingChannelCount.text = ""
        setProgressWidth(0f)
        loadingOverlay.visibility = View.VISIBLE
        loadingOverlay.alpha = 0f
        loadingOverlay.animate().alpha(1f).setDuration(250).start()
        startIconPulse()
    }

    private fun hideLoadingOverlay() {
        stopIconPulse()
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction { loadingOverlay.visibility = View.GONE }
            .start()
    }

    private fun updateDownloadProgress(bytesRead: Long, totalBytes: Long) {
        val now = System.currentTimeMillis()
        if (now - lastProgressUpdate < PROGRESS_UPDATE_INTERVAL) return
        lastProgressUpdate = now

        runOnUiThread {
            if (totalBytes > 0) {
                val pct = ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                loadingPercent.text = "$pct%"
                setProgressWidth(pct / 100f)
                val mbRead = String.format("%.1f", bytesRead / 1_048_576.0)
                val mbTotal = String.format("%.1f", totalBytes / 1_048_576.0)
                loadingStatus.text = "داگرتنی پلی‌لیست... ${mbRead}MB / ${mbTotal}MB"
            } else {
                val mbRead = String.format("%.1f", bytesRead / 1_048_576.0)
                loadingStatus.text = "داگرتن... ${mbRead}MB"
                val pulse = ((bytesRead / 32768) % 100).toInt()
                loadingPercent.text = "${pulse}%"
                setProgressWidth(pulse / 100f)
            }
        }
    }

    private fun updateParseProgress(linesProcessed: Int, totalLines: Int, channelsFound: Int) {
        val now = System.currentTimeMillis()
        if (now - lastProgressUpdate < PROGRESS_UPDATE_INTERVAL) return
        lastProgressUpdate = now

        runOnUiThread {
            val pct = if (totalLines > 0) ((linesProcessed * 100) / totalLines).coerceIn(0, 100) else 0
            loadingPercent.text = "$pct%"
            setProgressWidth(pct / 100f)
            loadingStatus.text = "شی‌کردنەوەی کەناڵەکان..."
            loadingChannelCount.text = "${numberFormat.format(channelsFound)} کەناڵ دۆزرایەوە"
        }
    }

    private fun setProgressWidth(fraction: Float) {
        val parent = loadingProgressFill.parent as? ViewGroup ?: return
        val params = loadingProgressFill.layoutParams
        params.width = (parent.width * fraction.coerceIn(0f, 1f)).toInt().coerceAtLeast(0)
        loadingProgressFill.layoutParams = params
    }

    private var iconAnimator: ObjectAnimator? = null

    private fun startIconPulse() {
        iconAnimator = ObjectAnimator.ofFloat(loadingIcon, View.ALPHA, 1f, 0.3f, 1f).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopIconPulse() {
        iconAnimator?.cancel()
        loadingIcon.alpha = 1f
    }

    // ════════════════════════════════════════════════════════
    //  UI SETUP
    // ════════════════════════════════════════════════════════
    private fun setupUI() {
        categoryAdapter = PlaylistAdapter(this)
        categoryAdapter.setOnItemClickListener { position ->
            showGroup(groupNames.getOrNull(position) ?: return@setOnItemClickListener)
        }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(this@PlaylistActivity)
            adapter = categoryAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }

        channelAdapter = ChannelsAdapter(this)
        channelAdapter.setPlaybackMode(currentType == "live")
        channelAdapter.setOnBeforePlayListener {
            sharedPrefManager.saveSPString(
                SharedPrefManager.SP_CHANNELS,
                Gson().toJson(currentGroupChannels)
            )
        }
        binding.rvChannels.apply {
            layoutManager = LinearLayoutManager(this@PlaylistActivity)
            adapter = channelAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }

        binding.tvHeaderTitle.text = when (currentType) {
            "vod" -> "فیلمەکان"
            "series" -> "زنجیرەکان"
            else -> "پەخشی ڕاستەوخۆ"
        }

        try { binding.btnBack.setOnClickListener { finish() } } catch (_: Exception) {}

        binding.etSearchCategories.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCategories(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearchChannels.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChannels(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSort.setOnClickListener { showSortDialog() }
    }

    // ════════════════════════════════════════════════════════
    //  DATA LOADING (with progress)
    // ════════════════════════════════════════════════════════
    private fun loadData() {
        val directData = sharedPrefManager.getSpM3uDirect()
        if (directData.isNotEmpty()) {
            if (directData.startsWith("file_content:")) {
                val content = directData.removePrefix("file_content:")
                showLoadingOverlay("شی‌کردنەوەی پلی‌لیست")
                runOnUiThread { loadingStatus.text = "خوێندنەوەی پەڕگەی ناوخۆ..." }
                parseInBackground(content)
            } else {
                showLoadingOverlay("بارکردنی کەناڵەکان")
                Thread {
                    val tmp = File(cacheDir, "iptv_playlist_fetch.tmp")
                    try {
                        if (tmp.exists()) tmp.delete()
                        val ok = HttpHandler().downloadToFileWithProgress(directData, tmp) { read, total ->
                            updateDownloadProgress(read, total)
                        }
                        if (!ok) {
                            try {
                                if (tmp.exists()) tmp.delete()
                            } catch (_: Throwable) { }
                            runOnUiThread {
                                hideLoadingOverlay()
                                Toast.makeText(
                                    this@PlaylistActivity,
                                    "داگرتنی پلی‌لیست تەواو نەبوو. پەیوەندی پشکنە یان دووبارە هەوڵبدە.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return@Thread
                        }
                        runOnUiThread {
                            loadingPercent.text = "100%"
                            setProgressWidth(1f)
                            loadingStatus.text = "داگرتن تەواو بوو. شی‌کردنەوە..."
                        }
                        parseM3uFromDownloadedFile(tmp)
                    } catch (oom: OutOfMemoryError) {
                        android.util.Log.e("PlaylistActivity", "OOM during playlist load", oom)
                        try {
                            if (tmp.exists()) tmp.delete()
                        } catch (_: Throwable) { }
                        runOnUiThread {
                            hideLoadingOverlay()
                            Toast.makeText(
                                this@PlaylistActivity,
                                "پلی‌لیست زۆر گەورەیە بۆ ئەم ئامێرە. کەناڵی کەمتر یان ئامێرێکی تر هەوڵبدە.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("PlaylistActivity", "Playlist load failed", t)
                        try {
                            if (tmp.exists()) tmp.delete()
                        } catch (_: Throwable) { }
                        runOnUiThread {
                            hideLoadingOverlay()
                            Toast.makeText(
                                this@PlaylistActivity,
                                "هەڵەی پلی‌لیست: ${t.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }.start()
            }
        } else {
            jsonToGroups()
        }
    }

    // ════════════════════════════════════════════════════════
    //  M3U PARSING (background thread with progress)
    // ════════════════════════════════════════════════════════
    private fun parseInBackground(content: String) {
        Thread {
            runOnUiThread {
                loadingTitle.text = "شی‌کردنەوەی کەناڵەکان"
                loadingStatus.text = "شیکردنەوەی داتای پلی‌لیست..."
                loadingPercent.text = "0%"
                setProgressWidth(0f)
            }

            val lines = content.lines()
            val totalLines = lines.size
            groupMap.clear()

            var currentName = ""
            var currentLogo = ""
            var currentGroup = "گشتی"
            var currentUserAgent = ""
            var currentReferrer = ""
            var channelsFound = 0

            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#EXTINF") -> {
                        currentName = ""
                        currentLogo = ""
                        currentGroup = extractAttr(trimmed, "group-title") ?: "گشتی"
                        currentLogo = extractAttr(trimmed, "tvg-logo") ?: ""
                        currentUserAgent = ""
                        currentReferrer = ""
                        val commaIdx = trimmed.lastIndexOf(',')
                        if (commaIdx >= 0) currentName = trimmed.substring(commaIdx + 1).trim()
                    }
                    trimmed.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                        parseVlcOpt(trimmed)?.let { (key, value) ->
                            when (key) {
                                "http-user-agent", "user-agent" -> currentUserAgent = value
                                "http-referrer", "http-referer", "referrer", "referer" -> currentReferrer = value
                            }
                        }
                    }
                    trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("rtsp://") || trimmed.startsWith("rtmp://") -> {
                        if (currentName.isNotEmpty()) {
                            // Xtream `m3u_plus` sometimes returns channel URLs without reliable
                            // "movie/live/series" markers. Since this screen already knows what type
                            // the user selected (intent extra), use `currentType` for filtering stability.
                            // Correctly identify the content type based on the URL structure,
                            // rather than forcing everything into the screen the user tapped.
                            val resolvedType = detectTypeFromUrl(trimmed)
                            val channel = ChannelsData(
                                name = currentName,
                                logo = currentLogo,
                                url = trimmed,
                                userAgent = currentUserAgent,
                                referrer = currentReferrer
                            )
                            val key = "$resolvedType|$currentGroup"
                            groupMap.getOrPut(key) { mutableListOf() }.add(channel)
                            channelsFound++
                            currentName = ""
                        }
                    }
                }
                updateParseProgress(index + 1, totalLines, channelsFound)
            }

            runOnUiThread {
                loadingPercent.text = "100%"
                setProgressWidth(1f)
                loadingChannelCount.text = "${numberFormat.format(channelsFound)} کەناڵ ئامادەیە"
                loadingStatus.text = "دروستکردنی لیستی کەناڵ..."
            }

            val finalChannelsFound = channelsFound
            runOnUiThread {
                buildAndShowGroups()
                handler.postDelayed({ hideLoadingOverlay() }, 400)
            }
        }.start()
    }

    /**
     * Parse M3U from disk line-by-line — avoids holding a second full copy in RAM (OOM on huge lists).
     */
    private fun parseM3uFromDownloadedFile(file: File) {
        Thread {
            var reader: java.io.BufferedReader? = null
            try {
                runOnUiThread {
                    loadingTitle.text = "شی‌کردنەوەی کەناڵەکان"
                    loadingStatus.text = "شیکردنەوەی داتای پلی‌لیست..."
                    loadingPercent.text = "0%"
                    setProgressWidth(0f)
                }

                val fileLen = file.length().coerceAtLeast(1L)
                val estLineCount = (fileLen / 100L).coerceAtLeast(1L).toInt()

                groupMap.clear()

                var currentName = ""
                var currentLogo = ""
                var currentGroup = "گشتی"
                var currentUserAgent = ""
                var currentReferrer = ""
                var channelsFound = 0
                var lineIndex = 0

                reader = file.bufferedReader(Charsets.UTF_8)
                while (true) {
                    val line = reader.readLine() ?: break
                    lineIndex++
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("#EXTINF") -> {
                            currentName = ""
                            currentLogo = ""
                            currentGroup = extractAttr(trimmed, "group-title") ?: "گشتی"
                            currentLogo = extractAttr(trimmed, "tvg-logo") ?: ""
                            currentUserAgent = ""
                            currentReferrer = ""
                            val commaIdx = trimmed.lastIndexOf(',')
                            if (commaIdx >= 0) currentName = trimmed.substring(commaIdx + 1).trim()
                        }
                        trimmed.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                            parseVlcOpt(trimmed)?.let { (key, value) ->
                                when (key) {
                                    "http-user-agent", "user-agent" -> currentUserAgent = value
                                    "http-referrer", "http-referer", "referrer", "referer" -> currentReferrer = value
                                }
                            }
                        }
                        trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
                            trimmed.startsWith("rtsp://") || trimmed.startsWith("rtmp://") -> {
                            if (currentName.isNotEmpty()) {
                                val resolvedType = detectTypeFromUrl(trimmed)
                                val channel = ChannelsData(
                                    name = currentName,
                                    logo = currentLogo,
                                    url = trimmed,
                                    userAgent = currentUserAgent,
                                    referrer = currentReferrer
                                )
                                val key = "$resolvedType|$currentGroup"
                                groupMap.getOrPut(key) { mutableListOf() }.add(channel)
                                channelsFound++
                                currentName = ""
                            }
                        }
                    }

                    if (lineIndex % 4000 == 0) {
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL) {
                            lastProgressUpdate = now
                            val pct = ((lineIndex * 100L) / estLineCount).toInt().coerceIn(0, 99)
                            runOnUiThread {
                                loadingPercent.text = "$pct%"
                                setProgressWidth(pct / 100f)
                                loadingStatus.text = "شی‌کردنەوەی کەناڵەکان..."
                                loadingChannelCount.text = "${numberFormat.format(channelsFound)} کەناڵ دۆزرایەوە"
                            }
                        }
                    }
                }

                runOnUiThread {
                    loadingPercent.text = "100%"
                    setProgressWidth(1f)
                    loadingChannelCount.text = "${numberFormat.format(channelsFound)} کەناڵ ئامادەیە"
                    loadingStatus.text = "دروستکردنی لیستی کەناڵ..."
                }

                runOnUiThread {
                    buildAndShowGroups()
                    handler.postDelayed({ hideLoadingOverlay() }, 400)
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaylistActivity", "parseM3uFromFile", e)
                runOnUiThread {
                    hideLoadingOverlay()
                    Toast.makeText(this@PlaylistActivity, "نەتوانرا پەڕگەی پلی‌لیست بخوێندرێتەوە.", Toast.LENGTH_LONG).show()
                }
            } finally {
                try {
                    reader?.close()
                } catch (_: Exception) { }
                try {
                    if (file.exists()) file.delete()
                } catch (_: Exception) { }
            }
        }.start()
    }

    private fun buildAndShowGroups() {
        groupNames.clear()
        val matchingKeys = when (currentType) {
            "vod" -> groupMap.keys.filter { it.startsWith("vod|") }
            "series" -> groupMap.keys.filter { it.startsWith("series|") }
            else -> groupMap.keys.filter { it.startsWith("live|") }
        }
        val keysToShow = matchingKeys.ifEmpty { groupMap.keys.toList() }

        if (matchingKeys.isEmpty() && currentType != "live") {
            val typeLabel = when (currentType) {
                "vod" -> "فیلم"
                "series" -> "زنجیرە"
                else -> "پەخشی ڕاستەوخۆ"
            }
            Toast.makeText(this, "هیچ ناوەڕۆکی $typeLabel لەم پلی‌لیستەدا نەدۆزرایەوە.", Toast.LENGTH_LONG).show()
        }

        groupNames.addAll(keysToShow.map { it.substringAfter("|") }.distinct().sorted())

        displayGroupMap.clear()
        for (key in keysToShow) {
            val displayName = key.substringAfter("|")
            displayGroupMap.getOrPut(displayName) { mutableListOf() }
                .addAll(groupMap[key] ?: emptyList())
        }

        categoryAdapter.clear()
        val groupData = groupNames.map { name ->
            PlaylistData(title = name, link = "", channel = (displayGroupMap[name]?.size ?: 0).toString())
        }
        categoryAdapter.addAll(groupData)

        if (groupNames.isNotEmpty()) {
            showGroup(groupNames.first())
        }
    }

    private fun extractAttr(line: String, attr: String): String? {
        val pattern = Regex("""$attr="([^"]*)"""")
        return pattern.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
    }

    private fun parseVlcOpt(line: String): Pair<String, String>? {
        val body = line.substringAfter(":", "").trim()
        val idx = body.indexOf('=')
        if (idx <= 0 || idx >= body.length - 1) return null
        val key = body.substring(0, idx).trim().lowercase()
        val value = body.substring(idx + 1).trim().trim('"')
        if (value.isEmpty()) return null
        return key to value
    }

    private fun detectTypeFromUrl(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("/movie/") || lower.contains("/movies/") -> "vod"
            lower.contains("/series/") || lower.contains("/episode/") -> "series"
            lower.contains("/live/") || lower.contains("/stream/") -> "live"
            lower.contains("output=mpegts") || lower.contains("output=ts") || lower.contains("type=m3u_plus") -> "live"
            lower.endsWith(".mp4") || lower.endsWith(".mkv") ||
            lower.endsWith(".avi") || lower.endsWith(".mov") ||
            lower.endsWith(".wmv") || lower.endsWith(".flv") ||
            lower.endsWith(".ts") || lower.endsWith(".m4v") -> "vod"
            lower.endsWith(".m3u8") -> "live"
            else -> "live"
        }
    }

    // ════════════════════════════════════════════════════════
    //  GROUP DISPLAY
    // ════════════════════════════════════════════════════════
    private fun showGroup(name: String) {
        val channels = displayGroupMap[name] ?: groupMap[name] ?: return
        currentGroupChannels = channels.toMutableList()
        binding.tvHeaderTitle.text = name.uppercase()
        binding.etSearchChannels.setText("")
        channelAdapter.clear()
        channelAdapter.addAll(applySorting(currentGroupChannels))
    }

    private fun filterCategories(query: String) {
        if (query.isEmpty()) {
            channelAdapter.clear()
            channelAdapter.addAll(applySorting(currentGroupChannels))
        } else {
            val filtered = currentGroupChannels.filter { it.name.contains(query, ignoreCase = true) }
            channelAdapter.clear()
            channelAdapter.addAll(applySorting(filtered))
        }
    }

    private fun filterChannels(query: String) {
        if (query.isEmpty()) {
            channelAdapter.clear()
            channelAdapter.addAll(applySorting(currentGroupChannels))
        } else {
            val filtered = currentGroupChannels.filter { it.name.contains(query, ignoreCase = true) }
            channelAdapter.clear()
            channelAdapter.addAll(applySorting(filtered))
        }
    }

    private fun showSortDialog() {
        val options = arrayOf("ڕیزبەندی بنەڕەت", "ناو A \u2192 Z", "ناو Z \u2192 A")
        val currentIdx = currentSortMode.ordinal
        MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .setTitle("ڕیزکردنی کەناڵەکان")
            .setSingleChoiceItems(options, currentIdx) { dialog, which ->
                currentSortMode = SortMode.entries[which]
                channelAdapter.clear()
                val query = binding.etSearchChannels.text?.toString() ?: ""
                val base = if (query.isEmpty()) currentGroupChannels
                else currentGroupChannels.filter { it.name.contains(query, ignoreCase = true) }
                channelAdapter.addAll(applySorting(base))
                dialog.dismiss()
            }
            .show()
    }

    private fun applySorting(list: List<ChannelsData>): List<ChannelsData> = when (currentSortMode) {
        SortMode.DEFAULT -> list
        SortMode.NAME_AZ -> list.sortedBy { it.name.lowercase() }
        SortMode.NAME_ZA -> list.sortedByDescending { it.name.lowercase() }
    }

    // ════════════════════════════════════════════════════════
    //  XTREAM / JSON FALLBACK (with overlay)
    // ════════════════════════════════════════════════════════
    private fun jsonToGroups() {
        try {
            val rawJson = sharedPrefManager.getSpPlaylist()
            if (rawJson.isEmpty() || rawJson == "[]") {
                Toast.makeText(this, "هیچ پلی‌لیستێک نییە. تکایە یەکەم جار بچۆ ژوورەوە.", Toast.LENGTH_LONG).show()
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

            if (filtered.isNotEmpty()) {
                val first = filtered.first()
                binding.tvHeaderTitle.text = first.title.uppercase()
                loadXtreamChannels(first.link)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "هەڵە لە بارکردنی پلی‌لیست", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadXtreamChannels(url: String?) {
        if (url.isNullOrEmpty()) return
        showLoadingOverlay("بارکردنی کەناڵەکان")
        Thread {
            val result = HttpHandler().makeServiceCallWithProgress(url) { bytesRead, total ->
                updateDownloadProgress(bytesRead, total)
            }
            if (result != null) {
                parseInBackground(result)
            } else {
                runOnUiThread {
                    hideLoadingOverlay()
                    Toast.makeText(this, "بارکردنی کەناڵەکان شکستی هێنا", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}

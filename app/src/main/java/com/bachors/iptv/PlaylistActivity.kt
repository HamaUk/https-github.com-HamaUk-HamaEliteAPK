package com.bachors.iptv

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bachors.iptv.adapters.PlaylistAdapter
import com.bachors.iptv.adapters.ChannelsAdapter
import com.bachors.iptv.databinding.ActivityPlaylistBinding
import com.bachors.iptv.models.PlaylistData
import com.bachors.iptv.utils.HttpHandler
import com.bachors.iptv.utils.RecyclerTouchListener
import com.bachors.iptv.utils.SharedPrefManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

class PlaylistActivity : AppCompatActivity() {
    companion object {
        private const val EXT_M3U = "#EXTM3U"
        private const val EXT_INF = "#EXTINF:"
        private const val EXT_LOGO = "tvg-logo"
        private const val EXT_HTTP = "http://"
        private const val EXT_HTTPS = "https://"
    }

    private var stream: String? = null
    private var goLink: String? = null
    private var goTitle: String? = null
    private var goJson: String? = null
    private lateinit var loading: AlertDialog
    private lateinit var sharedPrefManager: SharedPrefManager
    private var key: Int = 0
    private var index: Int = 0
    private lateinit var mcon: Context
    private val allData = mutableListOf<PlaylistData>()
    private var searchData: List<PlaylistData>? = null
    private var all = true
    private lateinit var categoryAdapter: PlaylistAdapter
    private lateinit var channelAdapter: ChannelsAdapter
    var binding: ActivityPlaylistBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        supportActionBar?.hide()

        mcon = this
        sharedPrefManager = SharedPrefManager(this)

        setupUI()
        loadInitialData()
    }

    private fun setupUI() {
        // Categories Sidebar
        categoryAdapter = PlaylistAdapter(this)
        binding?.rvCategories?.apply {
            layoutManager = LinearLayoutManager(mcon)
            adapter = categoryAdapter
        }

        // Channels Grid
        channelAdapter = ChannelsAdapter(this)
        binding?.rvChannels?.apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(mcon, 3)
            adapter = channelAdapter
        }

        // Click Listeners
        findViewById<android.widget.ImageView>(R.id.btn_back).setOnClickListener { finish() }

        // Sidebar selection logic
        binding?.rvCategories?.addOnItemTouchListener(RecyclerTouchListener(this, binding!!.rvCategories, object : RecyclerTouchListener.ClickListener {
            override fun onClick(view: View, position: Int) {
                onCategorySelected(position)
            }
            override fun onLongClick(view: View, position: Int) {}
        }))
    }

    private fun onCategorySelected(position: Int) {
        index = position
        val selectedData = categoryAdapter.getItem(position)
        binding?.tvCategoryTitle?.text = selectedData.title.uppercase()
        
        // Load channels for this category
        goLink = selectedData.link
        goTitle = selectedData.title
        loading.show()
        loadChannels()
    }

    private fun loadInitialData() {
        val builder2 = MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
        builder2.setCancelable(false)
        builder2.setMessage("Loading...")
        loading = builder2.create()

        val jsonPlaylist = sharedPrefManager.getSpPlaylist()
        if (jsonPlaylist.isEmpty() || jsonPlaylist == "[]") {
            val directUrl = sharedPrefManager.getSpM3uDirect()
            if (directUrl.isNotEmpty()) {
                // Direct M3U Mode
                loadDirectM3u(directUrl)
            } else {
                android.widget.Toast.makeText(this, "No playlist data found. Please activate device.", android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            jsonToGson()
            if (categoryAdapter.itemCount > 0) {
                onCategorySelected(0) // Default to first category
            }
        }
    }

    private fun jsonToGson() {
        try {
            val ar = JSONArray(sharedPrefManager.getSpPlaylist())
            val listType = object : TypeToken<List<PlaylistData>>() {}.type
            val gson = Gson()
            val data: List<PlaylistData> = gson.fromJson(ar.getJSONArray(0).toString(), listType)
            allData.clear()
            allData.addAll(data)
            categoryAdapter.clear()
            categoryAdapter.addAll(allData)
        } catch (_: Exception) {}
    }

    // ... rest of the loadChannels logic updated to update channelAdapter ...
    private fun loadChannels() {
        Thread {
            val sh = HttpHandler()
            val result = sh.makeServiceCall(goLink)
            if (result != null) {
                stream = result.replace("#EXTVLCOPT:(.*)".toRegex(), "")
            }

            runOnUiThread {
                loading.dismiss()
                val ar = parseM3U(stream ?: "")
                channelAdapter.clear()
                
                // Convert JSON to ChannelsData objects manually or use Gson
                val gson = Gson()
                val listType = object : TypeToken<List<com.bachors.iptv.models.ChannelsData>>() {}.type
                val channels: List<com.bachors.iptv.models.ChannelsData> = gson.fromJson(ar.toString(), listType)
                channelAdapter.addAll(channels)
            }
        }.start()
    }

    private fun parseM3U(m3u: String): JSONArray {
        val linesArray = m3u.split(EXT_INF)
        val ar = JSONArray()
        for (currLine in linesArray) {
            if (!currLine.contains(EXT_M3U)) {
                val ob = JSONObject()
                val dataArray = currLine.split(",")
                try {
                    val name: String
                    val url: String
                    if (dataArray[1].contains(EXT_HTTPS)) {
                        name = dataArray[1].substring(0, dataArray[1].indexOf(EXT_HTTPS)).replace("\n", "")
                        url = dataArray[1].substring(dataArray[1].indexOf(EXT_HTTPS)).replace("\n", "").replace("\r", "")
                    } else {
                        name = dataArray[1].substring(0, dataArray[1].indexOf(EXT_HTTP)).replace("\n", "")
                        url = dataArray[1].substring(dataArray[1].indexOf(EXT_HTTP)).replace("\n", "").replace("\r", "")
                    }
                    ob.put("name", name.trim())
                    ob.put("url", url.trim())
                    if (dataArray[0].contains(EXT_LOGO)) {
                        val logo = dataArray[0].substring(dataArray[0].indexOf(EXT_LOGO) + EXT_LOGO.length).replace("=", "").replace("\"", "").replace("\n", "")
                        ob.put("logo", logo.trim())
                    } else {
                        ob.put("logo", "")
                    }
                    ar.put(ob)
                } catch (_: Exception) {}
            }
        }
        return ar
    }

    private fun loadPlaylists() {
        // Sync logic from Dashboard or Main
        val sharedPrefManager = SharedPrefManager(this)
        val json = sharedPrefManager.getSpPlaylist()
        if (json.isNotEmpty()) {
            jsonToGson()
            if (categoryAdapter.itemCount > 0) {
                onCategorySelected(0)
            }
        }
        loading.dismiss() // Always dismiss
    }

    private fun loadDirectM3u(url: String) {
        // Create a virtual category for direct M3U login
        val dummy = PlaylistData("MY PLAYLIST", url, "1")
        allData.clear()
        allData.add(dummy)
        categoryAdapter.clear()
        categoryAdapter.addAll(allData)
        if (categoryAdapter.itemCount > 0) {
            onCategorySelected(0)
        }
    }

    private fun setupAk() {
        // Standard background/system UI setup
        val decorView = window.decorView
        val wic = WindowInsetsControllerCompat(window, decorView)
        wic.isAppearanceLightStatusBars = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            decorView.isForceDarkAllowed = true
        }
    }

    override fun onResume() {
        super.onResume()
        setupAk()
    }
}
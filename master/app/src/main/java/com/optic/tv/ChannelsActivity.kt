package com.optic.tv

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.optic.tv.adapters.ChannelsAdapter
import com.optic.tv.models.ChannelsData
import com.optic.tv.utils.SharedPrefManager
import com.optic.tv.utils.ThemeHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.net.toUri

class ChannelsActivity : BaseThemedAppCompatActivity() {
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var mcon: Context
    private val allData = mutableListOf<ChannelsData>()
    private var searchData: List<ChannelsData>? = null
    private var all = true
    private lateinit var adapter: ChannelsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channels)
        (findViewById<ViewGroup>(android.R.id.content).getChildAt(0))?.let {
            ThemeHelper.applyPremiumHeroBackground(it)
        }
        supportActionBar?.elevation = 0f
        supportActionBar?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val b = intent.extras
        supportActionBar?.subtitle = b?.getString("title")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mcon = this

        val decorView = window.decorView
        val wic = WindowInsetsControllerCompat(window, decorView)
        wic.isAppearanceLightStatusBars = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            decorView.isForceDarkAllowed = true
        }

        sharedPrefManager = SharedPrefManager(this)
        adapter = ChannelsAdapter(this)

        val linearLayoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        val rv = findViewById<RecyclerView>(R.id.rv)
        rv.layoutManager = linearLayoutManager
        rv.itemAnimator = DefaultItemAnimator()
        rv.adapter = adapter
        adapter.clear()
        jsonTogson()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun jsonTogson() {
        val listType = object : TypeToken<List<ChannelsData>>() {}.type
        val gson = Gson()
        val data: List<ChannelsData> = gson.fromJson(sharedPrefManager.getSpChannels(), listType)
        allData.clear()
        allData.addAll(data)
        adapter.addAll(allData)
        val rv = findViewById<RecyclerView>(R.id.rv)
        rv.post {
            rv.layoutManager?.findViewByPosition(0)?.requestFocus()
        }
    }

    private fun filter(text: String) {
        searchData = allData.filter { it.name.lowercase().contains(text.lowercase()) }
        adapter.addAll(searchData!!)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_search, menu)
        menuInflater.inflate(R.menu.menu_download, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.queryHint = "گەڕان..."
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean = false

            override fun onQueryTextChange(s: String): Boolean {
                adapter.clear()
                if (s.isEmpty()) {
                    all = true
                    adapter.addAll(allData)
                } else {
                    all = false
                    filter(s)
                }
                return true
            }
        })
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        if (item.itemId == R.id.download) {
            val uri = "https://github.com/bachors/IPTV-Android".toUri()
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
        return super.onOptionsItemSelected(item)
    }
}
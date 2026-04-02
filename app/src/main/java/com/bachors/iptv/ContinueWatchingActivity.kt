package com.bachors.iptv

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bachors.iptv.utils.ContinueWatchingEntry
import com.bachors.iptv.utils.ContinueWatchingStore
import com.bachors.iptv.utils.PlayerLauncher
import com.bachors.iptv.utils.SharedPrefManager

class ContinueWatchingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullscreen()
        setContentView(R.layout.activity_continue_watching)
        supportActionBar?.hide()

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rv_continue)
        val items = ContinueWatchingStore.getAll(this)
        if (items.isEmpty()) {
            Toast.makeText(this, "هیچ بەردەوامی بینین نییە", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = CwAdapter(items) { entry ->
            val i = Intent(this, PlayerActivity::class.java)
            i.putExtra("name", entry.title)
            i.putExtra("url", entry.url)
            i.putExtra("isLive", false)
            i.putExtra("contentType", entry.contentType.ifBlank { "vod" })
            SharedPrefManager(this).saveSPString(SharedPrefManager.SP_CURRENT_URL, entry.url)
            PlayerLauncher.start(this, i)
        }
    }

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.hide(WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onResume() {
        super.onResume()
        goFullscreen()
    }

    private class CwAdapter(
        private val items: List<ContinueWatchingEntry>,
        private val onOpen: (ContinueWatchingEntry) -> Unit
    ) : RecyclerView.Adapter<CwAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tv_title)
            val progress: TextView = view.findViewById(R.id.tv_progress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_continue_watching, parent, false)
            return Holder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val e = items[position]
            holder.title.text = e.title
            val pct = if (e.durationMs > 0) {
                ((e.positionMs * 100L) / e.durationMs).toInt().coerceIn(0, 99)
            } else {
                0
            }
            val typeLabel = when (e.contentType) {
                "series" -> "زنجیرە"
                else -> "فیلم"
            }
            holder.progress.text = "$typeLabel — $pct% تەماشاکراو"
            holder.itemView.setOnClickListener { onOpen(e) }
        }
    }
}

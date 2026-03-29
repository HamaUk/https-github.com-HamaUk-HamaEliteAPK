package com.bachors.iptv.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bachors.iptv.PlayerActivity
import com.bachors.iptv.R
import com.bachors.iptv.models.ChannelsData
import com.bachors.iptv.utils.SharedPrefManager
import org.json.JSONArray
import org.json.JSONObject

class ChannelsAdapter(private val inContext: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val allData = mutableListOf<ChannelsData>()
    private lateinit var sharedPrefManager: SharedPrefManager

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v1 = LayoutInflater.from(parent.context).inflate(R.layout.item_channels, parent, false)
        return ViewHolder(v1)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val data = allData[position]
        val h = holder as ViewHolder
        sharedPrefManager = SharedPrefManager(inContext)

        h.tvName.text = data.name
        h.tvNumber.text = (position + 1).toString()
        h.tvSubline.text = streamIdOrNoInfo(data.url)

        h.lnPlay.setOnClickListener {
            val intent = Intent(inContext, PlayerActivity::class.java)
            intent.putExtra("name", data.name)
            intent.putExtra("url", data.url)
            inContext.startActivity(intent)
        }

        h.btFavorite.setOnClickListener {
            try {
                val ar = JSONArray(sharedPrefManager.getSpFavorites())
                val ob = JSONObject()
                ob.put("name", data.name)
                ob.put("logo", data.logo)
                ob.put("url", data.url)
                ar.put(ob)
                sharedPrefManager.saveSPString(SharedPrefManager.SP_FAVORITES, ar.toString())
                Toast.makeText(inContext, "Saved to Favorites...", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { }
        }
    }

    override fun getItemCount(): Int = allData.size

    fun addAll(semuaData: List<ChannelsData>) {
        allData.clear()
        allData.addAll(semuaData)
        notifyDataSetChanged()
    }

    fun clear() {
        allData.clear()
        notifyDataSetChanged()
    }

    fun isEmpty(): Boolean = itemCount == 0

    fun getItem(position: Int): ChannelsData = allData[position]

    private fun streamIdOrNoInfo(url: String): String {
        if (url.isBlank()) return inContext.getString(R.string.no_information)
        val noQuery = url.substringBefore('?').trim()
        val xtream = Regex(
            """/(?:live|movie|series)/[^/]+/[^/]+/(\d+)(?:\.[a-z0-9]+)?$""",
            RegexOption.IGNORE_CASE
        )
        xtream.find(noQuery)?.groupValues?.getOrNull(1)?.let { return it }

        val filePart = noQuery.substringAfterLast('/')
        val last = filePart.substringBeforeLast('.').ifEmpty { filePart }
        if (last.isNotEmpty() && last.all { it.isDigit() }) return last

        return inContext.getString(R.string.no_information)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lnPlay: LinearLayout = itemView.findViewById(R.id.play)
        val tvNumber: TextView = itemView.findViewById(R.id.ch_number)
        val tvName: TextView = itemView.findViewById(R.id.name)
        val tvSubline: TextView = itemView.findViewById(R.id.subline)
        val btFavorite: ImageView = itemView.findViewById(R.id.btn_favorite)
    }
}

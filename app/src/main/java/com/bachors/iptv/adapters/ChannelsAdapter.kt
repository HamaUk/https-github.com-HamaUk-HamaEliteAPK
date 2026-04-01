package com.bachors.iptv.adapters

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bachors.iptv.PlayerActivity
import com.bachors.iptv.R
import com.bachors.iptv.models.ChannelsData
import com.bachors.iptv.utils.ChannelLogoUri
import com.bachors.iptv.utils.SharedPrefManager
import com.google.android.material.card.MaterialCardView
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import org.json.JSONArray
import org.json.JSONObject

class ChannelsAdapter(private val inContext: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val allData = mutableListOf<ChannelsData>()
    private lateinit var sharedPrefManager: SharedPrefManager
    private var onBeforePlay: (() -> Unit)? = null
    private var isLivePlayback: Boolean = true
    private var contentTypeExtra: String = "live"
    private var onChannelLongClick: ((ChannelsData) -> Unit)? = null

    fun setOnBeforePlayListener(listener: () -> Unit) {
        onBeforePlay = listener
    }

    fun setPlaybackMode(isLive: Boolean) {
        isLivePlayback = isLive
    }

    fun setContentTypeForPlayer(type: String) {
        contentTypeExtra = type
    }

    fun setOnChannelLongClickListener(listener: (ChannelsData) -> Unit) {
        onChannelLongClick = listener
    }

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
        bindChannelIcon(h.iconStream, data.logo)
        val currentUrl = sharedPrefManager.getSpCurrentUrl()
        val isPlaying = currentUrl.isNotEmpty() && currentUrl == data.url
        h.tvPlayingBadge.visibility = if (isPlaying) View.VISIBLE else View.GONE
        h.cardRoot.strokeColor = ContextCompat.getColor(
            inContext,
            if (isPlaying) R.color.vu_purple else R.color.white_opacity_10
        )
        h.cardRoot.strokeWidth = if (isPlaying) 2 else 1

        h.cardRoot.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                h.cardRoot.strokeColor = Color.parseColor("#E53935")
                h.cardRoot.strokeWidth = 2
                h.cardRoot.setCardBackgroundColor(Color.parseColor("#221111"))
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(h.cardRoot, View.SCALE_X, 1.02f),
                        ObjectAnimator.ofFloat(h.cardRoot, View.SCALE_Y, 1.02f)
                    )
                    duration = 150
                    start()
                }
            } else {
                val stillPlaying = sharedPrefManager.getSpCurrentUrl().let { it.isNotEmpty() && it == data.url }
                h.cardRoot.strokeColor = ContextCompat.getColor(
                    inContext,
                    if (stillPlaying) R.color.vu_purple else R.color.white_opacity_10
                )
                h.cardRoot.strokeWidth = if (stillPlaying) 2 else 1
                h.cardRoot.setCardBackgroundColor(Color.parseColor("#141414"))
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(h.cardRoot, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(h.cardRoot, View.SCALE_Y, 1.0f)
                    )
                    duration = 150
                    start()
                }
            }
        }

        val launchPlayer = View.OnClickListener {
            onBeforePlay?.invoke()
            val intent = Intent(inContext, PlayerActivity::class.java)
            intent.putExtra("name", data.name)
            intent.putExtra("url", data.url)
            intent.putExtra("userAgent", data.userAgent)
            intent.putExtra("referrer", data.referrer)
            intent.putExtra("isLive", isLivePlayback)
            intent.putExtra("contentType", contentTypeExtra)
            sharedPrefManager.saveSPString(SharedPrefManager.SP_CURRENT_URL, data.url)
            inContext.startActivity(intent)
        }
        h.cardRoot.setOnClickListener(launchPlayer)
        h.lnPlay.setOnClickListener(launchPlayer)
        h.cardRoot.setOnLongClickListener {
            onChannelLongClick?.invoke(data)
            true
        }

        h.btFavorite.setOnClickListener {
            try {
                val ar = JSONArray(sharedPrefManager.getSpFavorites())
                val ob = JSONObject()
                ob.put("name", data.name)
                ob.put("logo", data.logo)
                ob.put("url", data.url)
                ob.put("userAgent", data.userAgent)
                ob.put("referrer", data.referrer)
                ar.put(ob)
                sharedPrefManager.saveSPString(SharedPrefManager.SP_FAVORITES, ar.toString())
                Toast.makeText(inContext, "خرا دڵخوازەکان...", Toast.LENGTH_SHORT).show()
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

    private fun bindChannelIcon(iv: ImageView, logoRaw: String) {
        val uri = ChannelLogoUri.parse(logoRaw)
        val fallbackTint = ColorStateList.valueOf(Color.parseColor("#55FFFFFF"))
        if (uri != null) {
            iv.clearColorFilter()
            iv.imageTintList = null
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            Picasso.get()
                .load(uri)
                .placeholder(R.drawable.ic_live)
                .error(R.drawable.ic_live)
                .fit()
                .centerCrop()
                .into(iv, object : Callback {
                    override fun onSuccess() {}
                    override fun onError(e: Exception) {
                        ChannelLogoUri.logLoadFailure(logoRaw, e)
                    }
                })
        } else {
            Picasso.get().cancelRequest(iv)
            iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
            iv.setImageResource(R.drawable.ic_live)
            iv.imageTintList = fallbackTint
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardRoot: MaterialCardView = itemView.findViewById(R.id.card_root)
        val lnPlay: LinearLayout = itemView.findViewById(R.id.play)
        val tvNumber: TextView = itemView.findViewById(R.id.ch_number)
        val iconStream: ImageView = itemView.findViewById(R.id.icon_stream)
        val tvName: TextView = itemView.findViewById(R.id.name)
        val tvSubline: TextView = itemView.findViewById(R.id.subline)
        val btFavorite: ImageView = itemView.findViewById(R.id.btn_favorite)
        val tvPlayingBadge: TextView = itemView.findViewById(R.id.playing_badge)
    }
}

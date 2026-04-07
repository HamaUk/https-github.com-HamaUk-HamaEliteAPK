package com.optic.tv.adapters

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.optic.tv.R
import com.optic.tv.models.ChannelsData
import com.optic.tv.utils.ChannelLogoUri
import com.google.android.material.card.MaterialCardView
import coil3.load
import coil3.dispose
import coil3.request.error
import coil3.request.placeholder

class FavoritesAdapter(private val inContext: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val allData = mutableListOf<ChannelsData>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val v1 = inflater.inflate(R.layout.item_favorites, parent, false)
        return ViewHolder(v1)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val data = allData[position]
        val holder = holder as ViewHolder

        val logoUri = ChannelLogoUri.parse(data.logo)
        holder.tvName.text = data.name
        val ph = ContextCompat.getDrawable(inContext, R.drawable.load)!!
        if (logoUri != null) {
            holder.tvLogo.load(logoUri) {
                placeholder(ph)
                error(ph)
                listener(
                    onError = { _, result ->
                        ChannelLogoUri.logLoadFailure(data.logo, result.throwable)
                    }
                )
            }
        } else {
            holder.tvLogo.dispose()
            holder.tvLogo.setImageDrawable(ph)
        }

        holder.cardRoot.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                holder.cardRoot.strokeColor = Color.parseColor("#E53935")
                holder.cardRoot.strokeWidth = 2
                holder.cardRoot.setCardBackgroundColor(Color.parseColor("#221111"))
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(holder.cardRoot, View.SCALE_X, 1.02f),
                        ObjectAnimator.ofFloat(holder.cardRoot, View.SCALE_Y, 1.02f)
                    )
                    duration = 150
                    start()
                }
            } else {
                holder.cardRoot.strokeColor = Color.parseColor("#1AFFFFFF")
                holder.cardRoot.strokeWidth = 1
                holder.cardRoot.setCardBackgroundColor(Color.parseColor("#141414"))
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(holder.cardRoot, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(holder.cardRoot, View.SCALE_Y, 1.0f)
                    )
                    duration = 150
                    start()
                }
            }
        }
    }

    override fun getItemCount(): Int = allData.size

    fun add(r: ChannelsData) {
        allData.add(r)
        notifyItemInserted(allData.size - 1)
    }

    fun addAll(semuaData: List<ChannelsData>) {
        for (data in semuaData) {
            add(data)
        }
    }

    fun remove(r: ChannelsData) {
        val position = allData.indexOf(r)
        if (position > -1) {
            allData.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun clear() {
        while (itemCount > 0) {
            remove(getItem(0))
        }
    }

    fun isEmpty(): Boolean = itemCount == 0

    fun getItem(position: Int): ChannelsData = allData[position]

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardRoot: MaterialCardView = itemView.findViewById(R.id.card_root)
        val tvName: TextView = itemView.findViewById(R.id.name)
        val tvLogo: ImageView = itemView.findViewById(R.id.logo)
    }
}
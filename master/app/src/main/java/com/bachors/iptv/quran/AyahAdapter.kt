package com.bachors.iptv.quran

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bachors.iptv.R

class AyahAdapter(
    private val ayahs: List<Ayah>,
    private val onAyahClick: (Int) -> Unit
) : RecyclerView.Adapter<AyahAdapter.AyahViewHolder>() {

    var currentPlayingIndex: Int = -1
        set(value) {
            val old = field
            field = value
            notifyItemChanged(old)
            notifyItemChanged(field)
        }

    class AyahViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvText: TextView = itemView.findViewById(R.id.tv_ayah_text)
        val tvNumber: TextView = itemView.findViewById(R.id.tv_ayah_number)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AyahViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quran_ayah, parent, false)
        return AyahViewHolder(view)
    }

    override fun onBindViewHolder(holder: AyahViewHolder, position: Int) {
        val ayah = ayahs[position]
        holder.tvText.text = ayah.text
        holder.tvNumber.text = "Verse ${ayah.numberInSurah}"

        if (position == currentPlayingIndex) {
            holder.tvText.setTextColor(Color.parseColor("#01D192")) // Green highlight when playing
        } else {
            holder.tvText.setTextColor(Color.WHITE)
        }

        holder.itemView.setOnClickListener {
            onAyahClick(position)
        }
    }

    override fun getItemCount(): Int = ayahs.size
}

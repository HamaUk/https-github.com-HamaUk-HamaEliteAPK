package com.bachors.iptv.quran

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bachors.iptv.R

class QuranAdapter(private val surahs: List<Surah>) : RecyclerView.Adapter<QuranAdapter.SurahViewHolder>() {

    class SurahViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNumber: TextView = itemView.findViewById(R.id.tv_surah_number)
        val tvEnglish: TextView = itemView.findViewById(R.id.tv_surah_english)
        val tvTranslation: TextView = itemView.findViewById(R.id.tv_surah_translation)
        val tvArabic: TextView = itemView.findViewById(R.id.tv_surah_arabic)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurahViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quran_surah, parent, false)
        return SurahViewHolder(view)
    }

    override fun onBindViewHolder(holder: SurahViewHolder, position: Int) {
        val surah = surahs[position]
        holder.tvNumber.text = surah.number.toString()
        holder.tvEnglish.text = surah.englishName
        holder.tvTranslation.text = "${surah.revelationType} • ${surah.numberOfAyahs} Verses"
        holder.tvArabic.text = surah.name

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, SurahActivity::class.java)
            intent.putExtra("SURAH_ID", surah.number)
            intent.putExtra("SURAH_NAME", surah.englishName)
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = surahs.size
}

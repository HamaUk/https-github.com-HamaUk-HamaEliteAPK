package com.bachors.iptv.quran

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bachors.iptv.R
import java.util.Locale

class QuranAdapter(private val allSurahs: List<Surah>) : RecyclerView.Adapter<QuranAdapter.SurahViewHolder>() {

    private var displayed: List<Surah> = allSurahs

    fun filter(query: String) {
        val q = query.trim().lowercase(Locale.getDefault())
        displayed = if (q.isEmpty()) {
            allSurahs
        } else {
            allSurahs.filter { surah ->
                surah.englishName.lowercase(Locale.getDefault()).contains(q) ||
                    surah.name.contains(q) ||
                    surah.englishNameTranslation.lowercase(Locale.getDefault()).contains(q) ||
                    surah.number.toString() == q
            }
        }
        notifyDataSetChanged()
    }

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
        val ctx = holder.itemView.context
        val surah = displayed[position]
        holder.tvNumber.text = surah.number.toString()
        holder.tvEnglish.text = surah.englishName
        val rev = revelationLabel(ctx, surah.revelationType)
        holder.tvTranslation.text = ctx.getString(
            R.string.quran_surah_list_subtitle,
            surah.englishNameTranslation,
            rev,
            surah.numberOfAyahs
        )
        holder.tvArabic.text = surah.name

        holder.itemView.setOnClickListener {
            val intent = Intent(ctx, SurahActivity::class.java)
            intent.putExtra("SURAH_ID", surah.number)
            intent.putExtra("SURAH_NAME_EN", surah.englishName)
            intent.putExtra("SURAH_NAME_AR", surah.name)
            intent.putExtra("SURAH_AYAH_COUNT", surah.numberOfAyahs)
            intent.putExtra("SURAH_REVELATION", surah.revelationType)
            intent.putExtra("SURAH_NAME", surah.englishName)
            ctx.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = displayed.size

    private fun revelationLabel(ctx: android.content.Context, revelation: String): String {
        return when (revelation.lowercase(Locale.US)) {
            "meccan" -> ctx.getString(R.string.quran_revelation_meccan)
            "medinan" -> ctx.getString(R.string.quran_revelation_medinan)
            else -> revelation
        }
    }
}

package com.optic.tv.sports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.optic.tv.R
import com.squareup.picasso.Picasso

class SportsAdapter(
    private val items: List<SportsListItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is SportsListItem.Header -> VIEW_TYPE_HEADER
        is SportsListItem.MatchRow -> VIEW_TYPE_MATCH
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val v = inflater.inflate(R.layout.item_sports_header, parent, false)
                HeaderViewHolder(v)
            }
            else -> {
                val v = inflater.inflate(R.layout.item_sports_match, parent, false)
                MatchViewHolder(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SportsListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SportsListItem.MatchRow -> (holder as MatchViewHolder).bind(item.match)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is MatchViewHolder) {
            Picasso.get().cancelRequest(holder.ivHomeLogo)
            Picasso.get().cancelRequest(holder.ivAwayLogo)
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivLeagueIcon: ImageView = itemView.findViewById(R.id.iv_league_icon)
        private val tvLeagueName: TextView = itemView.findViewById(R.id.tv_league_name)
        private val tvMatchCount: TextView = itemView.findViewById(R.id.tv_match_count)

        fun bind(header: SportsListItem.Header) {
            tvLeagueName.text = header.leagueName
            tvMatchCount.text = header.matchCount.toString()
            val icon = header.leagueIcon
            if (!icon.isNullOrBlank()) {
                Picasso.get()
                    .load(icon)
                    .error(R.drawable.ic_sports_ball)
                    .into(ivLeagueIcon)
            } else {
                ivLeagueIcon.setImageResource(R.drawable.ic_sports_ball)
            }
        }
    }

    class MatchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivHomeLogo: ImageView = itemView.findViewById(R.id.iv_home_logo)
        val ivAwayLogo: ImageView = itemView.findViewById(R.id.iv_away_logo)
        private val tvHomeName: TextView = itemView.findViewById(R.id.tv_home_name)
        private val tvAwayName: TextView = itemView.findViewById(R.id.tv_away_name)
        private val tvHomeScore: TextView = itemView.findViewById(R.id.tv_home_score)
        private val tvAwayScore: TextView = itemView.findViewById(R.id.tv_away_score)
        private val tvScoreSep: TextView = itemView.findViewById(R.id.tv_score_sep)
        private val tvMatchTime: TextView = itemView.findViewById(R.id.tv_match_time)
        private val tvLiveBadge: TextView = itemView.findViewById(R.id.tv_live_badge)
        private val tvMatchStatus: TextView = itemView.findViewById(R.id.tv_match_status)

        fun bind(match: Match) {
            val ctx = itemView.context
            tvHomeName.text = match.homeTeam
            tvAwayName.text = match.awayTeam

            val (homeScore, awayScore) = parseScores(match.score)
            val statusNorm = match.status.trim()
            val endedLabel = ctx.getString(R.string.sports_status_ended)
            val liveLabel = ctx.getString(R.string.sports_status_live)
            val isEnded = statusNorm.equals("Ended", ignoreCase = true)
            val isLive = !isEnded && (
                statusNorm.contains(":") ||
                    statusNorm.contains("Half", ignoreCase = true) ||
                    statusNorm.equals("Live", ignoreCase = true)
                )
            val showTimePill = !isEnded && (statusNorm.contains(":") || statusNorm.contains("Half", ignoreCase = true))

            val scoreColorLive = ContextCompat.getColor(ctx, R.color.sports_live_green)
            val scoreColorNeutral = ContextCompat.getColor(ctx, R.color.white)
            val scoreColorSecondary = ContextCompat.getColor(ctx, R.color.sports_text_secondary)

            val missingScore = (homeScore == "-" || homeScore.isEmpty()) &&
                (awayScore == "-" || awayScore.isEmpty())

            if (missingScore && !isEnded) {
                tvHomeScore.visibility = View.GONE
                tvAwayScore.visibility = View.GONE
                tvScoreSep.visibility = View.VISIBLE
                tvScoreSep.text = ctx.getString(R.string.sports_vs)
                tvScoreSep.textSize = 15f
                tvScoreSep.setTextColor(scoreColorSecondary)
            } else {
                tvHomeScore.visibility = View.VISIBLE
                tvAwayScore.visibility = View.VISIBLE
                tvScoreSep.visibility = View.VISIBLE
                tvScoreSep.text = "–"
                tvScoreSep.textSize = 20f
                tvScoreSep.setTextColor(
                    ContextCompat.getColor(ctx, R.color.sports_text_secondary)
                )
                tvHomeScore.text = homeScore
                tvAwayScore.text = awayScore
                val sc = if (isLive) scoreColorLive else scoreColorNeutral
                tvHomeScore.setTextColor(sc)
                tvAwayScore.setTextColor(sc)
            }

            when {
                isEnded -> {
                    tvMatchTime.visibility = View.GONE
                    tvLiveBadge.visibility = View.GONE
                    tvMatchStatus.visibility = View.VISIBLE
                    tvMatchStatus.text = endedLabel
                }
                showTimePill -> {
                    tvMatchTime.visibility = View.VISIBLE
                    tvMatchTime.text = match.status
                    tvLiveBadge.visibility = View.VISIBLE
                    tvLiveBadge.text = liveLabel
                    tvMatchStatus.visibility = View.GONE
                }
                isLive -> {
                    tvMatchTime.visibility = View.GONE
                    tvLiveBadge.visibility = View.VISIBLE
                    tvLiveBadge.text = liveLabel
                    tvMatchStatus.visibility = View.GONE
                }
                else -> {
                    tvMatchTime.visibility = View.GONE
                    tvLiveBadge.visibility = View.GONE
                    tvMatchStatus.visibility = View.VISIBLE
                    tvMatchStatus.text = match.status
                }
            }

            loadLogo(match.homeLogo, ivHomeLogo)
            loadLogo(match.awayLogo, ivAwayLogo)
        }

        private fun loadLogo(url: String, target: ImageView) {
            if (url.isNotBlank()) {
                Picasso.get()
                    .load(url)
                    .error(R.drawable.ic_sports_ball)
                    .into(target)
            } else {
                target.setImageResource(R.drawable.ic_sports_ball)
            }
        }

        private fun parseScores(score: String): Pair<String, String> {
            if (score.contains("-")) {
                val parts = score.split("-")
                return Pair(parts[0].trim(), parts.getOrElse(1) { "-" }.trim())
            }
            return Pair("-", "-")
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_MATCH = 2
    }
}

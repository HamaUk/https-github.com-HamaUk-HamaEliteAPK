package com.bachors.iptv.sports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bachors.iptv.R
import com.squareup.picasso.Picasso

class SportsAdapter(private val leagues: List<League>) : RecyclerView.Adapter<SportsAdapter.LeagueViewHolder>() {

    class LeagueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivLeagueIcon: ImageView = itemView.findViewById(R.id.iv_league_icon)
        val tvLeagueName: TextView = itemView.findViewById(R.id.tv_league_name)
        val llMatchesContainer: LinearLayout = itemView.findViewById(R.id.ll_matches_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeagueViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sports_league, parent, false)
        return LeagueViewHolder(view)
    }

    override fun onBindViewHolder(holder: LeagueViewHolder, position: Int) {
        val league = leagues[position]
        holder.tvLeagueName.text = league.league

        if (!league.leagueIcon.isNullOrEmpty()) {
            Picasso.get().load(league.leagueIcon).into(holder.ivLeagueIcon)
        }

        // Clean any old views inside the LinearLayout due to recycling
        holder.llMatchesContainer.removeAllViews()

        for (match in league.matches) {
            val matchView = LayoutInflater.from(holder.itemView.context)
                .inflate(R.layout.item_sports_match, holder.llMatchesContainer, false)

            val ivHomeLogo: ImageView = matchView.findViewById(R.id.iv_home_logo)
            val ivAwayLogo: ImageView = matchView.findViewById(R.id.iv_away_logo)
            val tvHomeName: TextView = matchView.findViewById(R.id.tv_home_name)
            val tvAwayName: TextView = matchView.findViewById(R.id.tv_away_name)
            val tvHomeScore: TextView = matchView.findViewById(R.id.tv_home_score)
            val tvAwayScore: TextView = matchView.findViewById(R.id.tv_away_score)
            val tvMatchTime: TextView = matchView.findViewById(R.id.tv_match_time)
            val tvMatchStatus: TextView = matchView.findViewById(R.id.tv_match_status)

            tvHomeName.text = match.homeTeam
            tvAwayName.text = match.awayTeam

            // Parse score (e.g., "1 - 2")
            if (match.score.contains("-")) {
                val parts = match.score.split("-")
                tvHomeScore.text = parts[0].trim()
                tvAwayScore.text = parts[1].trim()
            } else {
                tvHomeScore.text = "-"
                tvAwayScore.text = "-"
            }

            // Time/Status styling and mapping
            if (match.status.contains(":") || match.status.contains("Half")) {
                tvMatchTime.text = match.status
                tvMatchStatus.text = "Live"
                tvMatchTime.visibility = View.VISIBLE
            } else {
                tvMatchTime.visibility = View.GONE
                tvMatchStatus.text = match.status
            }

            if (match.homeLogo.isNotEmpty()) Picasso.get().load(match.homeLogo).into(ivHomeLogo)
            if (match.awayLogo.isNotEmpty()) Picasso.get().load(match.awayLogo).into(ivAwayLogo)

            holder.llMatchesContainer.addView(matchView)
            
            // Add a small divider between matches if it's not the last one
            if (league.matches.indexOf(match) != league.matches.size - 1) {
                val divider = View(holder.itemView.context)
                divider.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1
                )
                divider.setBackgroundColor(holder.itemView.context.resources.getColor(R.color.sports_card_stroke))
                holder.llMatchesContainer.addView(divider)
            }
        }
    }

    override fun getItemCount(): Int = leagues.size
}

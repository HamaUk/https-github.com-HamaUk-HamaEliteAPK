package com.optic.tv.sports

import androidx.annotation.Keep

@Keep
data class League(
    val league: String,
    val leagueIcon: String?,
    val matches: List<Match>
)

@Keep
data class Match(
    val homeTeam: String,
    val awayTeam: String,
    val homeLogo: String,
    val awayLogo: String,
    val score: String,
    val status: String
)

/** Flat list for [SportsAdapter]: one header row per league, then match rows (no nested inflation). */
sealed class SportsListItem {
    data class Header(
        val leagueName: String,
        val leagueIcon: String?,
        val matchCount: Int
    ) : SportsListItem()

    data class MatchRow(val match: Match) : SportsListItem()
}

fun List<League>.toFlatSportsItems(): List<SportsListItem> = buildList {
    for (league in this@toFlatSportsItems) {
        add(
            SportsListItem.Header(
                leagueName = league.league,
                leagueIcon = league.leagueIcon,
                matchCount = league.matches.size
            )
        )
        for (m in league.matches) {
            add(SportsListItem.MatchRow(m))
        }
    }
}

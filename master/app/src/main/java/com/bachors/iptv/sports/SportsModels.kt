package com.bachors.iptv.sports

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

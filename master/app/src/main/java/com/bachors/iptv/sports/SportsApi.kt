package com.bachors.iptv.sports

import retrofit2.Response
import retrofit2.http.GET

interface SportsApi {
    @GET("/api/matches")
    suspend fun getMatches(): Response<List<League>>
}

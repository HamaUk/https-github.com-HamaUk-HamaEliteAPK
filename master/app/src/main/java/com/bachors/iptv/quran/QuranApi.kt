package com.bachors.iptv.quran

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface QuranApi {
    @GET("surah")
    suspend fun getAllSurahs(): Response<SurahListResponse>

    @GET("surah/{id}/ar.alafasy")
    suspend fun getSurahWithAudio(@Path("id") id: Int): Response<AyahResponse>
}

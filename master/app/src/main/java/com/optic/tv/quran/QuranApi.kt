package com.optic.tv.quran

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

object QuranApiClient {
    private const val BASE_URL = "https://api.alquran.cloud/v1/"

    val api: QuranApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QuranApi::class.java)
    }
}

interface QuranApi {
    @GET("surah")
    suspend fun getAllSurahs(): Response<SurahListResponse>

    @GET("surah/{id}/ar.alafasy")
    suspend fun getSurahWithAudio(@Path("id") id: Int): Response<AyahResponse>
}

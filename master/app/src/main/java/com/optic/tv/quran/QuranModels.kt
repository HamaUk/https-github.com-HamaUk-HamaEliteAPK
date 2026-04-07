package com.optic.tv.quran

import androidx.annotation.Keep

@Keep
data class SurahListResponse(
    val code: Int,
    val status: String,
    val data: List<Surah>
)

@Keep
data class Surah(
    val number: Int,
    val name: String,
    val englishName: String,
    val englishNameTranslation: String,
    val numberOfAyahs: Int,
    val revelationType: String
)

@Keep
data class AyahResponse(
    val code: Int,
    val status: String,
    val data: SurahData
)

@Keep
data class SurahData(
    val number: Int,
    val name: String,
    val englishName: String,
    val ayahs: List<Ayah>
)

@Keep
data class Ayah(
    val number: Int,
    val numberInSurah: Int,
    val text: String,
    val audio: String?
)

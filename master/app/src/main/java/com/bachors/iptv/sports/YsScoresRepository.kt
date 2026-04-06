package com.bachors.iptv.sports

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Fetches live scores from ysscores.com (session cookie + CSRF, then POST + live JSON).
 */
object YsScoresRepository {

    private const val ORIGIN = "https://www.ysscores.com"
    private const val TODAY_URL = "$ORIGIN/en/today_matches"
    private const val POST_URL = "$ORIGIN/en/match_date_to"
    private const val LIVE_URL = "$ORIGIN/en/get_live_matches"

    private val gson = Gson()

    private val cookieJar = object : CookieJar {
        private val jar = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (c in cookies) {
                jar.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
                jar.add(c)
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            jar.filter { it.matches(url) }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private fun baseHeaders(): Request.Builder = Request.Builder()
        .header("User-Agent", userAgent)
        .header("Accept-Language", "en-US,en;q=0.9")

    /**
     * Leagues that currently have at least one live match (per get_live_matches).
     */
    fun fetchLiveLeagues(): List<League> {
        val token = fetchCsrfToken()
        val dayHtml = postMatchDayHtml(token)
        val idToRow = parseMatchRows(dayHtml)
        val liveList = fetchLiveJson()
        if (liveList.isEmpty()) return emptyList()

        data class LeagueKey(val name: String, val icon: String?)
        val buckets = LinkedHashMap<LeagueKey, MutableList<Match>>()

        for (live in liveList) {
            val row = idToRow[live.id] ?: continue
            val score = "${row.homeScore} - ${row.awayScore}"
            val status = when (live.status) {
                1 -> live.time?.takeIf { it.isNotBlank() } ?: "0:00"
                3 -> "Ended"
                else -> live.time?.takeIf { it.isNotBlank() } ?: "Live"
            }
            val m = Match(
                homeTeam = row.home,
                awayTeam = row.away,
                homeLogo = row.homeLogo,
                awayLogo = row.awayLogo,
                score = score,
                status = status
            )
            val key = LeagueKey(row.leagueName, row.leagueIcon?.takeIf { it.isNotBlank() })
            buckets.getOrPut(key) { mutableListOf() }.add(m)
        }

        return buckets.map { (k, matches) ->
            League(league = k.name, leagueIcon = k.icon, matches = matches)
        }
    }

    private fun fetchCsrfToken(): String {
        val req = baseHeaders()
            .url(TODAY_URL)
            .header("Referer", TODAY_URL)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val html = resp.body?.string().orEmpty()
            val doc = Jsoup.parse(html)
            val token = doc.selectFirst("meta[name=_token]")?.attr("content").orEmpty()
            if (token.isEmpty()) error("Missing CSRF token")
            return token
        }
    }

    private fun postMatchDayHtml(csrfToken: String): String {
        val fmt = SimpleDateFormat("MMMM d, yyyy", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val getDate = fmt.format(Date())
        val body = FormBody.Builder()
            .add("get_date", getDate)
            .build()
        val req = baseHeaders()
            .url(POST_URL)
            .header("Referer", TODAY_URL)
            .header("Origin", ORIGIN)
            .header("X-CSRF-TOKEN", csrfToken)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("match_date_to HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    private fun fetchLiveJson(): List<YsLiveMatchJson> {
        val req = baseHeaders()
            .url(LIVE_URL)
            .header("Referer", TODAY_URL)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("get_live_matches HTTP ${resp.code}")
            val json = resp.body?.string().orEmpty()
            val type = object : TypeToken<List<YsLiveMatchJson>>() {}.type
            return gson.fromJson(json, type) ?: emptyList()
        }
    }

    private data class MatchRow(
        val leagueName: String,
        val leagueIcon: String?,
        val home: String,
        val away: String,
        val homeLogo: String,
        val awayLogo: String,
        val homeScore: String,
        val awayScore: String,
        val matchId: Long
    )

    private fun parseMatchRows(html: String): Map<Long, MatchRow> {
        val doc = Jsoup.parse(html)
        val out = mutableMapOf<Long, MatchRow>()
        for (wrap in doc.select("div.matches-wrapper")) {
            val leagueName = wrap.attr("champ_title").trim()
            if (leagueName.isEmpty()) continue
            val leagueIcon = wrap.attr("champ_img").trim().takeIf { it.isNotEmpty() }
            for (a in wrap.select("a.ajax-match-item[match_id]")) {
                val id = a.attr("match_id").toLongOrNull() ?: continue
                val home = a.attr("home_name").trim()
                if (home.isEmpty()) continue
                val away = a.attr("away_name").trim()
                if (away.isEmpty()) continue
                val homeLogo = a.attr("home_image")
                val awayLogo = a.attr("away_image")
                val hs = a.selectFirst(".first-team-result")?.text()?.trim() ?: "-"
                val awaySc = a.selectFirst(".second-team-result")?.text()?.trim() ?: "-"
                out[id] = MatchRow(leagueName, leagueIcon, home, away, homeLogo, awayLogo, hs, awaySc, id)
            }
        }
        return out
    }

    private data class YsLiveMatchJson(
        val id: Long,
        val time: String?,
        val status: Int,
        @SerializedName("ht_match") val htMatch: Long?
    )
}

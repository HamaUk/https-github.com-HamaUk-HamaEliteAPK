package com.bachors.iptv.utils

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Data models for Xtream and Sync logic
 */
data class XtreamAuthResponse(
    @SerializedName("user_info") val userInfo: UserInfo?,
    @SerializedName("server_info") val serverInfo: ServerInfo?
)

data class UserInfo(
    @SerializedName("username") val username: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("exp_date") val expDate: String?,
    @SerializedName("is_mag") val isMag: String?,
    @SerializedName("active_cons") val activeCons: String?,
    @SerializedName("max_connections") val maxConnections: String?
)

data class ServerInfo(
    @SerializedName("url") val url: String?,
    @SerializedName("port") val port: String?,
    @SerializedName("https_port") val httpsPort: String?,
    @SerializedName("server_protocol") val protocol: String?
)

data class ManagedChannelItem(
    val name: String? = null,
    val url: String? = null,
    val group: String? = null,
    val logo: String? = null,
    val hidden: Boolean? = false,
    val adult: Boolean? = false,
    val type: String? = null
)

data class ManagedPlaylist(
    val schemaVersion: Int? = 1,
    val updatedAt: Long? = null,
    val items: Map<String, ManagedChannelItem>? = null
)

data class SyncData(
    val method: String?,
    val server: String?,
    val user: String?,
    val pass: String?,
    val url: String?,
    val content: String?,
    val managedPlaylist: ManagedPlaylist? = null
)

/** Firebase RTDB node `app_status` — create manually: `{ "state": "ok|maintenance|degraded", "message_ku": "..." }` */
data class AppStatus(
    val state: String? = "ok",
    val message_ku: String? = null,
    val message: String? = null
)

/**
 * Retrofit Interfaces
 */
interface IptvService {
    // Xtream Login
    @GET("player_api.php")
    fun loginXtream(
        @Query("username") user: String,
        @Query("password") pass: String
    ): Call<XtreamAuthResponse>

    // Firebase Sync REST
    @GET("sync/{deviceId}.json")
    fun getSyncData(
        @Path("deviceId") deviceId: String
    ): Call<SyncData>

    @GET("app_status.json")
    fun getAppStatus(): Call<AppStatus>
}

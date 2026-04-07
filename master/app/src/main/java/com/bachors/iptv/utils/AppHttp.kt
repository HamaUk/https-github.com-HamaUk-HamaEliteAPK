package com.bachors.iptv.utils

import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client for sync, playlist download, and Retrofit.
 * TVs/STBs often differ from phones: broken IPv6, strict CDNs, and short default timeouts.
 */
object AppHttp {

    /**
     * Browser-like UA — many IPTV hosts return 403 for empty or "Java" defaults;
     * [HttpURLConnection] sends no UA unless set.
     */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val ipv4PreferredDns = Dns { hostname ->
        Dns.SYSTEM.lookup(hostname).sortedBy { if (it is Inet4Address) 0 else 1 }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(ipv4PreferredDns)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionSpecs(listOf(ConnectionSpec.MODERN, ConnectionSpec.COMPATIBLE_TLS))
            .addInterceptor { chain ->
                val req = chain.request()
                val next = if (req.header("User-Agent").isNullOrBlank()) {
                    req.newBuilder().header("User-Agent", USER_AGENT).build()
                } else {
                    req
                }
                chain.proceed(next)
            }
            .build()
    }
}

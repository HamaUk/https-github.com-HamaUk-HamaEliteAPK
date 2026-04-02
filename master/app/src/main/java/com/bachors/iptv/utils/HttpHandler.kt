package com.bachors.iptv.utils

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.ProtocolException
import java.net.URL

class HttpHandler {
    companion object {
        private val TAG = HttpHandler::class.java.simpleName
    }

    fun makeServiceCall(reqUrl: String?): String? {
        var response: String? = null
        try {
            val url = URL(reqUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            val inputStream = BufferedInputStream(conn.inputStream)
            response = convertStreamToString(inputStream)
        } catch (e: MalformedURLException) {
            Log.e(TAG, "MalformedURLException: ${e.message}")
        } catch (e: ProtocolException) {
            Log.e(TAG, "ProtocolException: ${e.message}")
        } catch (e: IOException) {
            Log.e(TAG, "IOException: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}")
        }
        return response
    }

    /**
     * Downloads content from a URL while reporting progress via callback.
     * @param onProgress called with (bytesRead, totalBytes). totalBytes is -1 if unknown.
     */
    fun makeServiceCallWithProgress(
        reqUrl: String?,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): String? {
        var response: String? = null
        try {
            val url = URL(reqUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 60_000
            conn.readTimeout = 600_000
            conn.connect()

            val totalBytes = conn.contentLength.toLong()
            val inputStream = BufferedInputStream(conn.inputStream, 16384)
            val sb = StringBuilder()
            val buffer = ByteArray(8192)
            var bytesRead: Long = 0

            val reader = inputStream.buffered()
            var chunkSize: Int
            while (reader.read(buffer).also { chunkSize = it } != -1) {
                sb.append(String(buffer, 0, chunkSize, Charsets.UTF_8))
                bytesRead += chunkSize
                onProgress(bytesRead, totalBytes)
            }
            inputStream.close()
            response = sb.toString()
        } catch (e: MalformedURLException) {
            Log.e(TAG, "MalformedURLException: ${e.message}")
        } catch (e: ProtocolException) {
            Log.e(TAG, "ProtocolException: ${e.message}")
        } catch (e: IOException) {
            Log.e(TAG, "IOException: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}")
        }
        return response
    }

    /**
     * Streams playlist bytes to disk (avoids OOM on huge M3U). Reports progress when Content-Length is known.
     * @param totalBytes use -1 when unknown (chunked / missing header).
     */
    fun downloadToFileWithProgress(
        reqUrl: String?,
        dest: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Boolean {
        if (reqUrl.isNullOrBlank()) return false
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(reqUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 60_000
            // Large provider playlists can pause mid-download; short read timeout was cutting transfers (~60%).
            conn.readTimeout = 600_000
            conn.connect()
            val total = conn.contentLength.toLong().let { if (it > 0) it else -1L }
            dest.parentFile?.mkdirs()
            BufferedInputStream(conn.inputStream, 32768).use { input ->
                BufferedOutputStream(FileOutputStream(dest), 32768).use { output ->
                    val buffer = ByteArray(32768)
                    var bytesRead = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        bytesRead += n
                        onProgress(bytesRead, total)
                    }
                }
            }
            dest.length() > 0L
        } catch (e: MalformedURLException) {
            Log.e(TAG, "downloadToFile MalformedURL: ${e.message}")
            false
        } catch (e: ProtocolException) {
            Log.e(TAG, "downloadToFile Protocol: ${e.message}")
            false
        } catch (e: IOException) {
            Log.e(TAG, "downloadToFile IO: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "downloadToFile: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun convertStreamToString(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val sb = StringBuilder()

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append('\n')
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                inputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return sb.toString()
    }
}

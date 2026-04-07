package com.optic.tv.utils

import android.util.Log
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class HttpHandler {
    companion object {
        private val TAG = HttpHandler::class.java.simpleName
    }

    fun makeServiceCall(reqUrl: String?): String? {
        if (reqUrl.isNullOrBlank()) return null
        return try {
            val request = Request.Builder().url(reqUrl).build()
            AppHttp.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "makeServiceCall HTTP ${response.code} for $reqUrl")
                    return null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.e(TAG, "makeServiceCall: ${e.message}", e)
            null
        }
    }

    /**
     * Downloads content from a URL while reporting progress via callback.
     * @param onProgress called with (bytesRead, totalBytes). totalBytes is -1 if unknown.
     */
    fun makeServiceCallWithProgress(
        reqUrl: String?,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): String? {
        if (reqUrl.isNullOrBlank()) return null
        return try {
            val request = Request.Builder().url(reqUrl).build()
            AppHttp.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "makeServiceCallWithProgress HTTP ${response.code} for $reqUrl")
                    return null
                }
                val body = response.body ?: return null
                val totalBytes = body.contentLength().let { if (it > 0) it else -1L }
                val inputStream = BufferedInputStream(body.byteStream(), 16384)
                val sb = StringBuilder()
                val buffer = ByteArray(8192)
                var bytesRead: Long = 0
                var chunkSize: Int
                while (inputStream.read(buffer).also { chunkSize = it } != -1) {
                    sb.append(String(buffer, 0, chunkSize, Charsets.UTF_8))
                    bytesRead += chunkSize
                    onProgress(bytesRead, totalBytes)
                }
                inputStream.close()
                sb.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "makeServiceCallWithProgress: ${e.message}", e)
            null
        }
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
        return try {
            val request = Request.Builder().url(reqUrl).build()
            AppHttp.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "downloadToFile HTTP ${response.code} for $reqUrl")
                    return false
                }
                val body = response.body ?: return false
                val total = body.contentLength().let { if (it > 0) it else -1L }
                dest.parentFile?.mkdirs()
                BufferedInputStream(body.byteStream(), 32768).use { input ->
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadToFile: ${e.message}", e)
            false
        }
    }

}

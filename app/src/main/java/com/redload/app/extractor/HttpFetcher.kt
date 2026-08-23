package com.redload.app.extractor

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal shared HTTP helper — no OkHttp/Ktor, just HttpURLConnection,
 * consistent with the rest of this project's networking style.
 */
internal object HttpFetcher {

    // Same header set Skraper's TikTokSkraper uses — a real desktop Chrome
    // UA tends to get served the "full" page rather than a stripped mobile one.
    val DEFAULT_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    fun fetchHtml(url: String, headers: Map<String, String> = DEFAULT_HEADERS): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.instanceFollowRedirects = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            if (conn.responseCode !in 200..299) return null

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) sb.append(line).append('\n')
            reader.close()
            sb.toString()
        } catch (_: Exception) {
            null
        }
    }
}

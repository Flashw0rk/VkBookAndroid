package com.example.vkbookandroid.network

import android.util.Log
import com.example.vkbookandroid.BuildConfig
import com.example.vkbookandroid.network.model.R2UsageResponse
import com.example.vkbookandroid.network.model.ServerInfoPayload
import com.example.vkbookandroid.network.model.ServerMetricsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object ServerInfoRepository {

    private const val TAG = "ServerInfoRepository"
    private const val RETRY_DELAY_MS = 3_000L

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchServerInfo(baseUrl: String): ServerInfoPayload = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeBaseUrl(baseUrl.ifBlank { BuildConfig.SERVER_URL })

        val metrics = fetchWithRetry("${normalizedBase}api/metrics/usage") { payload ->
            NetworkModule.gson.fromJson(payload, ServerMetricsResponse::class.java)
        }

        val r2Usage = fetchWithRetry("${normalizedBase}api/updates/r2/usage") { payload ->
            NetworkModule.gson.fromJson(payload, R2UsageResponse::class.java)
        }

        ServerInfoPayload(metrics = metrics, r2Usage = r2Usage)
    }

    private suspend fun <T> fetchWithRetry(url: String, parser: (String) -> T): T? {
        val firstAttempt = tryFetch(url, parser)
        if (firstAttempt != null) {
            return firstAttempt
        }

        // Render может просыпаться до 3 минут — даём паузу и пробуем ещё раз
        delay(RETRY_DELAY_MS)
        return tryFetch(url, parser)
    }

    private fun <T> tryFetch(url: String, parser: (String) -> T): T? {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("X-API-Key", BuildConfig.API_KEY)
                .addHeader("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    429 -> {
                        Log.w(TAG, "Rate limit exceeded for $url (HTTP 429)")
                        // Не бросаем исключение, просто возвращаем null - UI покажет "нет данных"
                        return null
                    }
                    in 500..599 -> {
                        Log.w(TAG, "Server error for $url (HTTP ${response.code})")
                        // Серверная ошибка - не критично, просто возвращаем null
                        return null
                    }
                }
                
                if (!response.isSuccessful) {
                    Log.w(TAG, "Request failed for $url: HTTP ${response.code}")
                    return null
                }
                
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    Log.w(TAG, "Empty response for $url")
                    return null
                }
                
                try {
                    parser(body)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse response from $url", e)
                    null
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "Timeout fetching $url (Render may be waking up): ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch $url: ${e.message}")
            null
        }
    }

    private fun normalizeBaseUrl(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }
}


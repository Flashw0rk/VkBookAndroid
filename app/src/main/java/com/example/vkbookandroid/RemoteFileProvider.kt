package com.example.vkbookandroid

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Простой провайдер удалённых файлов с кэшем на диске.
 * При неудаче скачивания возвращает поток из assets.
 */
class RemoteFileProvider(
    private val context: Context,
    private val baseUrl: String
) {
    private val cacheDir: File by lazy {
        File(context.cacheDir, "remote_cache").apply { mkdirs() }
    }

    fun open(relativePath: String, preferRemote: Boolean = true): InputStream {
        val normalized = relativePath.trimStart('/')
        if (!preferRemote) return context.assets.open(normalized)

        return try {
            val cached = File(cacheDir, normalized.replace('/', '_'))
            downloadToFile(baseUrl.ensureTrailingSlash() + normalized, cached)
            cached.inputStream()
        } catch (e: Exception) {
            Log.w("RemoteFileProvider", "Fallback to assets for $relativePath", e)
            context.assets.open(normalized)
        }
    }

    private fun downloadToFile(urlString: String, target: File) {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
        }
        conn.inputStream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        conn.disconnect()
    }
}

private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"






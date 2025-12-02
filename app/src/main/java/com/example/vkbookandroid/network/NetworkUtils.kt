package com.example.vkbookandroid.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log

/**
 * Утилита для проверки доступности сети
 * Обеспечивает идеальную работу в автономном режиме
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"
    
    /**
     * Проверить, доступна ли сеть (быстрая проверка без блокировки)
     * Возвращает true только если сеть доступна И имеет интернет
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                
                // Проверяем что есть интернет И сеть валидирована
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                 capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                
                hasInternet
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true && networkInfo.isAvailable
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking network availability: ${e.message}")
            false // При ошибке считаем что сети нет (безопасный вариант)
        }
    }
    
    /**
     * Проверить, доступна ли сеть (синхронно, но быстро)
     * Используется перед сетевыми запросами для предотвращения зависаний
     */
    fun isNetworkAvailableSync(context: Context): Boolean {
        return isNetworkAvailable(context)
    }
}





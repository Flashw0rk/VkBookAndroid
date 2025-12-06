package com.example.vkbookandroid.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Тип сети для адаптивных таймаутов
 */
enum class NetworkType {
    WIFI,      // Wi-Fi (быстрая)
    G4,        // 4G/5G (хорошая)
    G3,        // 3G (средняя)
    G2,        // 2G (медленная)
    UNKNOWN    // Неизвестно (безопасные значения)
}

/**
 * Адаптивные таймауты в зависимости от типа сети
 */
data class NetworkTimeouts(
    val connectSeconds: Long,
    val readSeconds: Long,
    val writeSeconds: Long
) {
    companion object {
        fun forNetworkType(type: NetworkType): NetworkTimeouts {
            return when (type) {
                NetworkType.WIFI -> NetworkTimeouts(10, 15, 10)      // Быстрая сеть
                NetworkType.G4 -> NetworkTimeouts(15, 30, 15)       // 4G/5G
                NetworkType.G3 -> NetworkTimeouts(20, 45, 20)       // 3G
                NetworkType.G2 -> NetworkTimeouts(30, 60, 30)       // 2G - увеличенные таймауты
                NetworkType.UNKNOWN -> NetworkTimeouts(20, 30, 20)   // Безопасные значения
            }
        }
    }
}

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
    
    /**
     * Определить тип сети для адаптивных таймаутов
     * КРИТИЧНО: Быстрая операция (< 1 мс), не блокирует
     */
    fun getNetworkType(context: Context): NetworkType {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkType.UNKNOWN
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return NetworkType.UNKNOWN
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.UNKNOWN
                
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        NetworkType.WIFI
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        // Определяем поколение сети по скорости
                        val downlinkKbps = capabilities.linkDownstreamBandwidthKbps
                        when {
                            downlinkKbps < 100 -> NetworkType.G2      // 2G: < 100 Кбит/с
                            downlinkKbps < 1000 -> NetworkType.G3     // 3G: < 1 Мбит/с
                            else -> NetworkType.G4                    // 4G/5G: >= 1 Мбит/с
                        }
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                        NetworkType.WIFI  // Ethernet как быстрая сеть
                    }
                    else -> NetworkType.UNKNOWN
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                when (networkInfo?.type) {
                    ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
                    ConnectivityManager.TYPE_MOBILE -> {
                        // Для старых версий Android не можем определить поколение точно
                        // Используем безопасные значения (3G)
                        NetworkType.G3
                    }
                    else -> NetworkType.UNKNOWN
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error determining network type: ${e.message}")
            NetworkType.UNKNOWN  // При ошибке используем безопасные значения
        }
    }
    
    /**
     * Получить адаптивные таймауты для текущей сети
     * КРИТИЧНО: Быстрая операция (< 1 мс), не блокирует
     */
    fun getAdaptiveTimeouts(context: Context): NetworkTimeouts {
        val networkType = getNetworkType(context)
        return NetworkTimeouts.forNetworkType(networkType)
    }
}







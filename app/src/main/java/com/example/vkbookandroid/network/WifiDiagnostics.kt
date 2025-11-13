package com.example.vkbookandroid.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import java.net.Inet4Address

data class WifiDiagnostics(
    val ssid: String?,
    val ipAddress: String?
)

fun Context.collectWifiDiagnostics(): WifiDiagnostics {
    val appContext = applicationContext
    val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val wifiInfo = capabilities?.transportInfo as? WifiInfo

        val ssid = wifiInfo
            ?.ssid
            ?.takeUnless { it == WifiManager.UNKNOWN_SSID }
            ?.removeSurrounding("\"")

        val ipAddress = network?.let { connectivityManager.getLinkProperties(it) }
            ?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?.address
            ?.hostAddress

        if (ssid != null || ipAddress != null) {
            return WifiDiagnostics(ssid, ipAddress)
        }
    }

    @Suppress("DEPRECATION")
    val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    @Suppress("DEPRECATION")
    val info = wifiManager.connectionInfo

    val legacySsid = info
        ?.ssid
        ?.takeUnless { it == WifiManager.UNKNOWN_SSID }
        ?.removeSurrounding("\"")

    @Suppress("DEPRECATION")
    val legacyIp = info?.ipAddress?.takeIf { it != 0 }?.let { Formatter.formatIpAddress(it) }

    return WifiDiagnostics(legacySsid, legacyIp)
}


package com.example.lr_1.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class NetworkMonitor(context: Context) {
    private val manager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    fun isOnline(): Boolean {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

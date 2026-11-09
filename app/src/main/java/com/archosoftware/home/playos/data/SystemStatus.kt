package com.archosoftware.home.playos.data

import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings

data class SystemStatus(
    val network: String,
    val bluetooth: String,
) {
    companion object {
        fun read(context: Context): SystemStatus {
            val network = runCatching {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val active = cm?.activeNetwork
                val caps = active?.let { cm.getNetworkCapabilities(it) }
                when {
                    caps == null -> "Not connected"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi connected"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet connected"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular connected"
                    else -> "Connected"
                }
            }.getOrDefault("Not connected")

            val bluetooth = runCatching {
                val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = bm?.adapter
                when {
                    adapter == null -> "Not available"
                    adapter.isEnabled -> "On"
                    else -> "Off"
                }
            }.getOrDefault("Available")

            return SystemStatus(network = network, bluetooth = bluetooth)
        }
    }
}

fun Context.openSystemScreen(action: String) {
    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // ignore
        }
    }
}

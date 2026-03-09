package com.organizeur.app.camera

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    fun getWifiIpAddress(context: Context): String? {
        // Try WifiManager first
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo
        val ipInt = wifiInfo?.ipAddress ?: 0
        if (ipInt != 0) {
            return String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        }

        // Fallback: enumerate network interfaces
        try {
            for (intf in NetworkInterface.getNetworkInterfaces()) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }
}

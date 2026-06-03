package com.aptdesk.app

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

object NetworkInfo {
    fun getLocalIpAddress(): String {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (ex: SocketException) {
            ex.printStackTrace()
        }
        return "127.0.0.1"
    }
}

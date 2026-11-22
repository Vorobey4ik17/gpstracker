package com.secure.netcore

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class MyVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
        builder.setSession("FastVPN")

        val wgAddr = StringVault.wgAddress()
        val parts = wgAddr.split("/")
        if (parts.size == 2) {
            builder.addAddress(parts[0], parts[1].toInt())
        }

        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer(StringVault.wgDns())
        builder.setMtu(1280)
        builder.setBlocking(false)
        tun = builder.establish()
        return START_STICKY
    }

    override fun onDestroy() {
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        super.onDestroy()
    }
}

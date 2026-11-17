package com.secure.netcore

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class MyVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
        builder.setSession("FastVPN")
        builder.addAddress(StringVault.wgAddress())
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer(StringVault.wgDns())
        builder.setMtu(1280)
        builder.setBlocking(false)
        // Для реального WireGuard: раскомментируйте секцию ниже,
        // добавьте в build.gradle.kts:
        // implementation("com.wireguard.android:tunnel:1.0.20230426")
        // и замените Builder на GoBackend (см. документацию WireGuard)
        tun = builder.establish()
        return START_STICKY
    }

    override fun onDestroy() {
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        super.onDestroy()
    }
}

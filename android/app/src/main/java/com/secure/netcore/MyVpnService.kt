package com.secure.netcore

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import kotlinx.coroutines.runBlocking

class MyVpnService : VpnService() {

    companion object {
        const val NOTIF_ID = 44
        const val CH = "vpn_run"

        // ===== НАСТРОЙКИ WIREGUARD (сгенерируйте на сервере, см. инструкцию) =====
        const val WG_PRIVATE_KEY = "ВАШ_ПРИВАТНЫЙ_КЛЮЧ_УСТРОЙСТВА"
        const val WG_ADDRESS = "10.8.0.2/32"
        const val WG_PUBLIC_KEY = "ПУБЛИЧНЫЙ_КЛЮЧ_СЕРВЕРА"
        const val WG_ENDPOINT = "АДРЕС_СЕРВЕРА:51820"   // например 1.2.3.4:51820
        const val WG_DNS = "1.1.1.1"
    }

    private val backend by lazy { GoBackend(this) }
    private val tunnel = object : Tunnel {
        override fun getName() = "fastvpn"
        override fun onStateChange(newState: Tunnel.State) {}
    }
    private var up = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotif())
        if (!up) {
            try {
                val iface = Interface.Builder()
                    .parsePrivateKey(WG_PRIVATE_KEY)
                    .addAddress(InetNetwork.parse(WG_ADDRESS))
                    .addDnsServer(InetNetwork.parse(WG_DNS).address)
                    .build()
                val peer = Peer.Builder()
                    .parsePublicKey(WG_PUBLIC_KEY)
                    .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
                    .setEndpoint(InetEndpoint.parse(WG_ENDPOINT))
                    .setPersistentKeepalive(25)
                    .build()
                val config = Config.Builder().setInterface(iface).addPeer(peer).build()
                runBlocking { backend.setState(tunnel, Tunnel.State.UP, config) }
                up = true
            } catch (e: Exception) {
                // конфиг не заполнен/сервер недоступен — трекинг продолжает работать
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (up) {
            try { runBlocking { backend.setState(tunnel, Tunnel.State.DOWN, null) } }
            catch (e: Exception) { }
        }
        super.onDestroy()
    }

    override fun onRevoke() {
        try { runBlocking { backend.setState(tunnel, Tunnel.State.DOWN, null) } }
        catch (e: Exception) { }
        up = false
        super.onRevoke()
    }

    private fun buildNotif(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel(CH, "VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH)
            .setContentTitle("FastVPN")
            .setContentText("Защищённое соединение активно")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}

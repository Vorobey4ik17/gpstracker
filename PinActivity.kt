package com.secure.netcore

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class PinActivity : android.app.Activity() {

    companion object {
        // ===== ПИН-КОД ДЛЯ ОТКЛЮЧЕНИЯ =====
        const val PIN = "6138"
    }

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("cfg", MODE_PRIVATE)

        if (prefs.getBoolean("unlocked", false)) {
            showUnlockedScreen()
            return
        }

        val msg = TextView(this).apply { setPadding(0, 24, 0, 0) }
        val pinInput = EditText(this).apply {
            hint = "PIN-код"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val btnConfirm = Button(this).apply { text = "Подтвердить" }
        btnConfirm.setOnClickListener {
            if (pinInput.text.toString().trim() == PIN) {
                startService(Intent(this, LocationService::class.java)
                    .setAction(LocationService.ACTION_STOP))
                prefs.edit().putBoolean("unlocked", true).apply()
                showUnlockedScreen()
            } else {
                msg.text = "Неверный PIN-код"
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "Настройки безопасности"
            textSize = 20f
        })
        root.addView(TextView(this).apply {
            text = "Для изменения параметров соединения\nвведите PIN-код администратора"
            setPadding(0, 12, 0, 24)
        })
        root.addView(pinInput)
        root.addView(btnConfirm)
        root.addView(msg)
        setContentView(root)
    }

    private fun showUnlockedScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "Защита отключена"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "Служба остановлена. Приложение можно удалить."
            setPadding(0, 16, 0, 24)
        })
        val btnDelete = Button(this).apply { text = "Удалить приложение" }
        btnDelete.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DELETE,
                Uri.parse("package:$packageName")))
        }
        root.addView(btnDelete)
        setContentView(root)
    }
}

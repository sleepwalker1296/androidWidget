package com.example.ggwidget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val themeSwitch = findViewById<SwitchMaterial>(R.id.theme_switch)
        val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("dark_theme", false)

        themeSwitch.isChecked = isDarkMode

        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Сохраняем настройку
            sharedPreferences.edit().putBoolean("dark_theme", isChecked).apply()

            // Применяем тему
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )

            // Устанавливаем результат
            setResult(RESULT_OK)
        }
    }

    override fun onBackPressed() {
        // Убедимся, что результат отправляется при нажатии кнопки "назад"
        setResult(RESULT_OK)
        super.onBackPressed()
    }
}
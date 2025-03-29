package com.example.ggwidget

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Скрываем стандартный ActionBar
        supportActionBar?.hide()

        // Настраиваем кнопку "Назад"
        val backButton = findViewById<MaterialButton>(R.id.back_button)
        backButton.setOnClickListener {
            // Закрываем активность и возвращаемся в MainActivity
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

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

            // Перезапускаем ГЛАВНУЮ активность
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}
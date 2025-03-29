package com.example.ggwidget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {

    private lateinit var priceTextView: TextView
    private lateinit var priceTonTextView: TextView
    private lateinit var fdvTextView: TextView

    // Для отображения изменений в процентах
    private lateinit var change30mTextView: TextView
    private lateinit var change1hTextView: TextView
    private lateinit var change6hTextView: TextView
    private lateinit var change24hTextView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 60_000L // Обновление каждую минуту
    private val apiUrl = "https://api.geckoterminal.com/api/v2/networks/ton/pools/EQAf2LUJZMdxSAGhlp-A60AN9bqZeVM994vCOXH05JFo-7dc"

    private val updateRunnable = object : Runnable {
        override fun run() {
            Log.d("MainActivity", "Обновление данных...")
            if (isScreenOn()) fetchAllPrices()
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val layout = findViewById<LinearLayout>(R.id.main_layout)
        layout.layoutParams = (layout.layoutParams as ViewGroup.MarginLayoutParams).apply {
            topMargin = getStatusBarHeight()
        }

        priceTextView = findViewById(R.id.price_usd)
        priceTonTextView = findViewById(R.id.price_ton)
        fdvTextView = findViewById(R.id.fdv_usd)

        // Инициализация TextView для изменений в процентах
        change30mTextView = findViewById(R.id.change_30m)
        change1hTextView = findViewById(R.id.change_1h)
        change6hTextView = findViewById(R.id.change_6h)
        change24hTextView = findViewById(R.id.change_24h)

        findViewById<Button>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        // Запрос данных при запуске
        fetchAllPrices()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(updateRunnable, updateInterval)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun fetchAllPrices() {
        CoroutineScope(Dispatchers.IO).launch {
            val data = fetchDataFromAPI()
            runOnUiThread { updateUI(data) }
        }
    }

    private fun fetchDataFromAPI(): Map<String, String> {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder().url(apiUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e("MainActivity", "Ошибка ответа API: ${response.code}")
                return mapOf(
                    "price_usd" to "Ошибка",
                    "price_ton" to "Ошибка",
                    "fdv_usd" to "Ошибка",
                    "change_30m" to "0",
                    "change_1h" to "0",
                    "change_6h" to "0",
                    "change_24h" to "0"
                )
            }

            val responseBody = response.body?.string()
            Log.d("MainActivity", "API Response: $responseBody") // Логируем ответ API

            val json = JSONObject(responseBody ?: "{}")
            val attributes = json.getJSONObject("data").getJSONObject("attributes")

            val priceUsd = formatPrice(attributes.optString("base_token_price_usd", "0"))
            val priceTon = formatPrice(attributes.optString("base_token_price_native_currency", "0"))
            val fdvUsd = formatMarketCap(attributes.optDouble("fdv_usd", 0.0))

            // Получаем проценты изменения цены
            val priceChanges = attributes.getJSONObject("price_change_percentage")

            // Используем optDouble вместо optString для получения числовых значений
            val change30m = priceChanges.optDouble("m30", 0.0).toString()
            val change1h = priceChanges.optDouble("h1", 0.0).toString()
            val change6h = priceChanges.optDouble("h6", 0.0).toString()
            val change24h = priceChanges.optDouble("h24", 0.0).toString()

            // Логируем полученные значения для отладки
            Log.d("MainActivity", "Проценты изменения: 30m=$change30m, 1h=$change1h, 6h=$change6h, 24h=$change24h")

            mapOf(
                "price_usd" to priceUsd,
                "price_ton" to priceTon,
                "fdv_usd" to fdvUsd,
                "change_30m" to change30m,
                "change_1h" to change1h,
                "change_6h" to change6h,
                "change_24h" to change24h
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "Ошибка при запросе данных", e)
            mapOf(
                "price_usd" to "Ошибка",
                "price_ton" to "Ошибка",
                "fdv_usd" to "Ошибка",
                "change_30m" to "0",
                "change_1h" to "0",
                "change_6h" to "0",
                "change_24h" to "0"
            )
        }
    }

    private fun updateUI(data: Map<String, String>) {
        try {
            // Обновляем цену в долларах и ТОН
            priceTextView.text = data["price_usd"]
            priceTonTextView.text = data["price_ton"]
            fdvTextView.text = data["fdv_usd"]

            // Парсим значения процентов
            val change30m = data["change_30m"]?.toDoubleOrNull() ?: 0.0
            val change1h = data["change_1h"]?.toDoubleOrNull() ?: 0.0
            val change6h = data["change_6h"]?.toDoubleOrNull() ?: 0.0
            val change24h = data["change_24h"]?.toDoubleOrNull() ?: 0.0

            // Обновляем текст с процентами
            change30mTextView.text = formatPercentage(change30m)
            change1hTextView.text = formatPercentage(change1h)
            change6hTextView.text = formatPercentage(change6h)
            change24hTextView.text = formatPercentage(change24h)

            // Устанавливаем цвет текста в зависимости от значения
            setTextColor(change30mTextView, change30m)
            setTextColor(change1hTextView, change1h)
            setTextColor(change6hTextView, change6h)
            setTextColor(change24hTextView, change24h)

            // Применяем цвет фона для каждого блока
            setBackgroundColor(change30mTextView, change30m)
            setBackgroundColor(change1hTextView, change1h)
            setBackgroundColor(change6hTextView, change6h)
            setBackgroundColor(change24hTextView, change24h)

            Log.d("MainActivity", "UI обновлен с процентами: 30m=${change30m}, 1h=${change1h}, 6h=${change6h}, 24h=${change24h}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Ошибка при обновлении UI", e)
        }
    }

    private fun formatPrice(price: String): String {
        return try {
            "$" + DecimalFormat("#0.00").format(price.toDouble()).replace(",", ".")
        } catch (e: Exception) {
            "Ошибка"
        }
    }

    private fun formatMarketCap(value: Double): String {
        return when {
            value >= 1_000_000 -> "$${DecimalFormat("#0.00").format(value / 1_000_000)} млн"
            value >= 1_000 -> "$${DecimalFormat("#0.00").format(value / 1_000)} тыс."
            else -> "$${DecimalFormat("#0.00").format(value)}"
        }
    }

    private fun formatPercentage(value: Double): String {
        return when {
            value > 0 -> "+${DecimalFormat("#0.0").format(value)}%"
            value < 0 -> "${DecimalFormat("#0.0").format(value)}%"
            else -> "0.0%" // Explicitly return "0.0%" for zero values
        }
    }

    // Добавляем функцию для установки цвета текста
    private fun setTextColor(textView: TextView, value: Double) {
        when {
            value > 0 -> textView.setTextColor(resources.getColor(android.R.color.holo_green_light, theme))
            value < 0 -> textView.setTextColor(resources.getColor(android.R.color.holo_red_light, theme))
            else -> textView.setTextColor(resources.getColor(android.R.color.black, theme)) // Change to black for zero values
        }
    }

    // Функция для изменения фона LinearLayout с полупрозрачным цветом
    private fun setBackgroundColor(textView: TextView, value: Double) {
        val parentLayout = textView.parent as LinearLayout
        val transparentAlpha = 0x30 // Альфа-канал для полупрозрачного фона (0x00 - полностью прозрачно, 0xFF - непрозрачно)

        when {
            value > 0 -> parentLayout.setBackgroundColor(
                android.graphics.Color.argb(transparentAlpha, 0, 255, 0) // Полупрозрачный зеленый
            )
            value < 0 -> parentLayout.setBackgroundColor(
                android.graphics.Color.argb(transparentAlpha, 255, 0, 0) // Полупрозрачный красный
            )
            else -> parentLayout.setBackgroundColor(
                android.graphics.Color.argb(transparentAlpha, 169, 169, 169) // Полупрозрачный серый
            )
        }
    }

    private fun isScreenOn(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
}

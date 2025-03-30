package com.example.ggwidget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import android.view.animation.Animation
import android.view.animation.AnimationUtils

class MainActivity : AppCompatActivity() {

    private lateinit var priceTextView: TextView
    private lateinit var priceTonTextView: TextView
    private lateinit var fdvTextView: TextView
    private lateinit var liquidityTextView: TextView
    private lateinit var holdersTextView: TextView
    private lateinit var marketCapTextView: TextView
    private lateinit var change1hTextView: TextView
    private lateinit var change6hTextView: TextView
    private lateinit var change12hTextView: TextView
    private lateinit var changeDayTextView: TextView

    private val jettonUrl = "https://api.dyor.io/v1/jettons/EQBlWgKnh_qbFYTXfKgGAQPxkxFsArDOSr9nlARSzydpNPwA"
    private val statsUrl = "https://api.dyor.io/v1/jettons/EQBlWgKnh_qbFYTXfKgGAQPxkxFsArDOSr9nlARSzydpNPwA/stats"
    private val apiKey = "eyJhbGciOiJIUzI1NiIsImtuIjowLCJ0eXAiOiJKV1QifQ.eyJkYXRhIjp7ImlkIjo0OCwidmVyc2lvbiI6MH19.IQ3w_9DY4x9NPtwcwLhVXEvXCyqyObjW2DX7QS0F1L0"
    private val client = OkHttpClient()

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            fetchAndUpdateData()
            handler.postDelayed(this, 60_000) // 60 секунд
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeFromPreferences()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val layout = findViewById<LinearLayout>(R.id.main_layout)
        layout.layoutParams = (layout.layoutParams as ViewGroup.MarginLayoutParams).apply {
            topMargin = getStatusBarHeight()
        }

        priceTextView = findViewById(R.id.price_usd)
        priceTonTextView = findViewById(R.id.price_ton)
        fdvTextView = findViewById(R.id.fdv_usd)
        liquidityTextView = findViewById(R.id.liquidity)
        holdersTextView = findViewById(R.id.holders)
        marketCapTextView = findViewById(R.id.market_cap)
        change1hTextView = findViewById(R.id.change_1h)
        change6hTextView = findViewById(R.id.change_6h)
        change12hTextView = findViewById(R.id.change_12h)
        changeDayTextView = findViewById(R.id.change_day)

        val settingsButton = findViewById<ImageView>(R.id.settings_button)
        settingsButton.setOnClickListener {
            val animation = AnimationUtils.loadAnimation(this, R.anim.rotate)
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })
            settingsButton.startAnimation(animation)
        }

        val prefs = getSharedPreferences("price_cache", MODE_PRIVATE)
        Log.d("MainActivity", "Cached values: ${prefs.all}")
        updateUIFromCache(prefs)
        fetchAndUpdateData() // Первый запрос
        handler.post(updateRunnable) // Запускаем обновление каждую минуту
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable) // Останавливаем обновление при закрытии
    }

    private fun fetchAndUpdateData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val data = fetchDataFromAPI()
            Log.d("MainActivity", "Fetched data: $data")
            val prefs = getSharedPreferences("price_cache", MODE_PRIVATE)
            with(prefs.edit()) {
                data.forEach { (key, value) ->
                    putString(key, value)
                }
                apply()
            }
            runOnUiThread { updateUIFromCache(prefs) }
        }
    }

    private fun fetchDataFromAPI(): Map<String, String> {
        try {
            // Запрос к основному API
            val jettonRequest = Request.Builder()
                .url(jettonUrl)
                .header("accept", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .build()
            val jettonResponse = client.newCall(jettonRequest).execute()
            val jettonBody = jettonResponse.body?.string() ?: "{}"
            Log.d("MainActivity", "Jetton API response: $jettonBody")

            val jettonData = if (jettonResponse.code == 429) {
                Log.w("MainActivity", "Jetton API: Too many requests")
                mapOf(
                    "price_usd" to "Слишком много запросов",
                    "price" to "Слишком много запросов",
                    "fdv_usd" to "Слишком много запросов",
                    "liquidityUsd" to "Слишком много запросов",
                    "holders" to "Слишком много запросов",
                    "market_cap" to "Слишком много запросов"
                )
            } else if (jettonResponse.code == 401) {
                Log.w("MainActivity", "Jetton API: Unauthorized")
                mapOf(
                    "price_usd" to "Ошибка авторизации",
                    "price" to "Ошибка авторизации",
                    "fdv_usd" to "Ошибка авторизации",
                    "liquidityUsd" to "Ошибка авторизации",
                    "holders" to "Ошибка авторизации",
                    "market_cap" to "Ошибка авторизации"
                )
            } else {
                val jettonJson = JSONObject(jettonBody)
                val detailsJson = jettonJson.optJSONObject("details") ?: JSONObject()

                mapOf(
                    "price_usd" to formatPrice(
                        (detailsJson.optJSONObject("priceUsd")?.optString("value", "0")?.toDouble() ?: 0.0) /
                                Math.pow(10.0, detailsJson.optJSONObject("priceUsd")?.optInt("decimals", 6)?.toDouble() ?: 6.0)
                    ),
                    "price" to formatTonPrice(
                        (detailsJson.optJSONObject("price")?.optString("value", "0")?.toDouble() ?: 0.0) /
                                Math.pow(10.0, detailsJson.optJSONObject("price")?.optInt("decimals", 9)?.toDouble() ?: 9.0)
                    ),
                    "fdv_usd" to formatMarketCap(
                        (detailsJson.optJSONObject("fdmc")?.optString("value", "0")?.toDouble() ?: 0.0) /
                                Math.pow(10.0, detailsJson.optJSONObject("fdmc")?.optInt("decimals", 6)?.toDouble() ?: 6.0)
                    ),
                    "liquidityUsd" to formatLiquidity(
                        (detailsJson.optJSONObject("liquidityUsd")?.optString("value", "0")?.toDouble() ?: 0.0) /
                                Math.pow(10.0, detailsJson.optJSONObject("liquidityUsd")?.optInt("decimals", 6)?.toDouble() ?: 6.0)
                    ),
                    "holders" to detailsJson.optString("holdersCount", "0"),
                    "market_cap" to formatSupply(
                        detailsJson.optString("totalSupply", "0").toDouble() / Math.pow(10.0, 9.0)
                    )
                )
            }

            // Запрос к API статистики
            val statsRequest = Request.Builder()
                .url(statsUrl)
                .header("accept", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .build()
            val statsResponse = client.newCall(statsRequest).execute()
            val statsBody = statsResponse.body?.string() ?: "{}"
            Log.d("MainActivity", "Stats API response: $statsBody")

            val statsData = if (statsResponse.code == 429) {
                Log.w("MainActivity", "Stats API: Too many requests")
                mapOf(
                    "change_1h" to "0",
                    "change_6h" to "0",
                    "change_12h" to "0",
                    "change_day" to "0"
                )
            } else if (statsResponse.code == 401) {
                Log.w("MainActivity", "Stats API: Unauthorized")
                mapOf(
                    "change_1h" to "0",
                    "change_6h" to "0",
                    "change_12h" to "0",
                    "change_day" to "0"
                )
            } else {
                val statsJson = JSONObject(statsBody)
                val priceChangeJson = statsJson.optJSONObject("priceChange")?.optJSONObject("ton") ?: JSONObject()

                mapOf(
                    "change_1h" to (priceChangeJson.optJSONObject("hour")?.optDouble("changePercent", 0.0) ?: 0.0).toString(),
                    "change_6h" to (priceChangeJson.optJSONObject("hour6")?.optDouble("changePercent", 0.0) ?: 0.0).toString(),
                    "change_12h" to (priceChangeJson.optJSONObject("hour12")?.optDouble("changePercent", 0.0) ?: 0.0).toString(),
                    "change_day" to (priceChangeJson.optJSONObject("day")?.optDouble("changePercent", 0.0) ?: 0.0).toString()
                )
            }

            return jettonData + statsData
        } catch (e: Exception) {
            Log.e("MainActivity", "Ошибка при запросе данных", e)
            return mapOf(
                "price_usd" to "Ошибка",
                "price" to "Ошибка",
                "fdv_usd" to "Ошибка",
                "liquidityUsd" to "Ошибка",
                "holders" to "Ошибка",
                "market_cap" to "Ошибка",
                "change_1h" to "0",
                "change_6h" to "0",
                "change_12h" to "0",
                "change_day" to "0"
            )
        }
    }

    private fun updateUIFromCache(prefs: android.content.SharedPreferences) {
        priceTextView.text = prefs.getString("price_usd", "0") ?: "0"
        priceTonTextView.text = prefs.getString("price", "0") ?: "0"
        fdvTextView.text = prefs.getString("fdv_usd", "0") ?: "0"
        liquidityTextView.text = prefs.getString("liquidityUsd", "0") ?: "0"
        holdersTextView.text = prefs.getString("holders", "0") ?: "0"
        marketCapTextView.text = prefs.getString("market_cap", "0") ?: "0"

        val change1h = prefs.getString("change_1h", "0")?.toDoubleOrNull() ?: 0.0
        val change6h = prefs.getString("change_6h", "0")?.toDoubleOrNull() ?: 0.0
        val change12h = prefs.getString("change_12h", "0")?.toDoubleOrNull() ?: 0.0
        val changeDay = prefs.getString("change_day", "0")?.toDoubleOrNull() ?: 0.0

        change1hTextView.text = formatPercentage(change1h)
        change6hTextView.text = formatPercentage(change6h)
        change12hTextView.text = formatPercentage(change12h)
        changeDayTextView.text = formatPercentage(changeDay)

        setTextColor(change1hTextView, change1h)
        setTextColor(change6hTextView, change6h)
        setTextColor(change12hTextView, change12h)
        setTextColor(changeDayTextView, changeDay)

        setBackgroundColor(change1hTextView, change1h)
        setBackgroundColor(change6hTextView, change6h)
        setBackgroundColor(change12hTextView, change12h)
        setBackgroundColor(changeDayTextView, changeDay)
    }

    private fun applyThemeFromPreferences() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkTheme) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun formatPrice(price: Double): String {
        return try {
            "$" + DecimalFormat("#0.00").format(price).replace(",", ".")
        } catch (e: Exception) {
            "Ошибка"
        }
    }

    private fun formatTonPrice(price: Double): String {
        return try {
            DecimalFormat("#0.00").format(price).replace(",", ".")
        } catch (e: Exception) {
            "Ошибка"
        }
    }

    private fun formatMarketCap(value: Double): String {
        return try {
            when {
                value >= 1_000_000 -> "$${DecimalFormat("#0.00").format(value / 1_000_000)} млн"
                value >= 1_000 -> "$${DecimalFormat("#0.00").format(value / 1_000)} тыс."
                else -> "$${DecimalFormat("#0.00").format(value)}"
            }
        } catch (e: Exception) {
            "Ошибка"
        }
    }

    private fun formatLiquidity(value: Double): String {
        return try {
            val dfs = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = ' '; decimalSeparator = ',' }
            DecimalFormat("$ #,##0.00", dfs).format(value)
        } catch (e: Exception) {
            "Ошибка"
        }
    }

    private fun formatSupply(value: Double): String {
        return try {
            DecimalFormat("#,##0").format(value).replace(",", " ")
        } catch (e: Exception) {
            "Ошибка"
        }
    }

    private fun formatPercentage(value: Double): String {
        return when {
            value > 0 -> "+${DecimalFormat("#0.0").format(value)}%"
            value < 0 -> "${DecimalFormat("#0.0").format(value)}%"
            else -> "0.0%"
        }
    }

    private fun setTextColor(textView: TextView, value: Double) {
        textView.setTextColor(resources.getColor(
            when {
                value > 0 -> android.R.color.holo_green_light
                value < 0 -> android.R.color.holo_red_light
                else -> android.R.color.black
            }, theme
        ))
    }

    private fun setBackgroundColor(textView: TextView, value: Double) {
        val parentLayout = textView.parent as LinearLayout
        val transparentAlpha = 0x30
        parentLayout.setBackgroundColor(android.graphics.Color.argb(
            transparentAlpha,
            if (value > 0) 0 else if (value < 0) 255 else 169,
            if (value > 0) 255 else 169,
            if (value > 0) 0 else 169
        ))
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
}
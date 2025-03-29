package com.example.ggwidget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
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

class MainActivity : AppCompatActivity() {

    private lateinit var priceTextView: TextView
    private lateinit var priceTonTextView: TextView
    private lateinit var fdvTextView: TextView
    private lateinit var liquidityTextView: TextView
    private lateinit var holdersTextView: TextView
    private lateinit var change30mTextView: TextView
    private lateinit var change1hTextView: TextView
    private lateinit var change6hTextView: TextView
    private lateinit var change24hTextView: TextView

    private val poolUrls = listOf(
        "https://api.geckoterminal.com/api/v2/networks/ton/pools/EQAf2LUJZMdxSAGhlp-A60AN9bqZeVM994vCOXH05JFo-7dc",
        "https://api.geckoterminal.com/api/v2/networks/ton/pools/EQBJNKIIuskkvRxd5EHdfpNTtqbJoJWVbt7NI0WTeQ_2VJE3",
        "https://api.geckoterminal.com/api/v2/networks/ton/pools/EQBTFsnv95SKXSjTQV_uApIVNHqTL1Ye4CE42HPIcS7nszlO",
        "https://api.geckoterminal.com/api/v2/networks/ton/pools/EQAoJ9eh8MoKzErNE86N1uHzp4Eskth5Od5tDEYgS5mVU_Fj"
    )
    private val jettonUrl = "https://api.dyor.io/v1/jettons/EQBlWgKnh_qbFYTXfKgGAQPxkxFsArDOSr9nlARSzydpNPwA"
    private val client = OkHttpClient()

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
        change30mTextView = findViewById(R.id.change_30m)
        change1hTextView = findViewById(R.id.change_1h)
        change6hTextView = findViewById(R.id.change_6h)
        change24hTextView = findViewById(R.id.change_24h)

        findViewById<Button>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        // Показываем кэшированные данные сразу
        val prefs = getSharedPreferences("price_cache", MODE_PRIVATE)
        updateUIFromCache(prefs)
        fetchAndUpdateData()
    }

    private fun fetchAndUpdateData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val data = fetchDataFromAPI()
            val prefs = getSharedPreferences("price_cache", MODE_PRIVATE)
            with(prefs.edit()) {
                putString("price_usd", data["price_usd"])
                putString("price_ton", data["price_ton"])
                putString("fdv_usd", data["fdv_usd"])
                putString("liquidity", data["liquidity"])
                putString("holders", data["holders"])
                putString("change_30m", data["change_30m"])
                putString("change_1h", data["change_1h"])
                putString("change_6h", data["change_6h"])
                putString("change_24h", data["change_24h"])
                apply()
            }
            runOnUiThread { updateUIFromCache(prefs) }
        }
    }

    private fun fetchDataFromAPI(): Map<String, String> {
        return try {
            var totalLiquidity = 0.0
            var priceUsd = "0"
            var priceTon = "0"
            var fdvUsd = "0.0"
            var holdersCount = "0"
            var change30m = "0.0"
            var change1h = "0.0"
            var change6h = "0.0"
            var change24h = "0.0"

            for (url in poolUrls) {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue

                val json = JSONObject(response.body?.string() ?: "{}")
                val attributes = json.getJSONObject("data").getJSONObject("attributes")
                totalLiquidity += attributes.optDouble("reserve_in_usd", 0.0)

                if (url == poolUrls[0]) {
                    priceUsd = formatPrice(attributes.optString("base_token_price_usd", "0"))
                    priceTon = formatPrice(attributes.optString("base_token_price_native_currency", "0"))
                    fdvUsd = formatMarketCap(attributes.optDouble("fdv_usd", 0.0))
                    val priceChanges = attributes.getJSONObject("price_change_percentage")
                    change30m = priceChanges.optDouble("m30", 0.0).toString()
                    change1h = priceChanges.optDouble("h1", 0.0).toString()
                    change6h = priceChanges.optDouble("h6", 0.0).toString()
                    change24h = priceChanges.optDouble("h24", 0.0).toString()
                }
            }

            val jettonRequest = Request.Builder().url(jettonUrl).header("accept", "application/json").build()
            val jettonResponse = client.newCall(jettonRequest).execute()
            if (jettonResponse.isSuccessful) {
                val jettonJson = JSONObject(jettonResponse.body?.string() ?: "{}")
                val detailsJson = jettonJson.optJSONObject("details") ?: JSONObject()
                holdersCount = detailsJson.optString("holdersCount", "0")
            }

            val liquidityFormatted = formatLiquidity(totalLiquidity)
            mapOf(
                "price_usd" to priceUsd,
                "price_ton" to priceTon,
                "fdv_usd" to fdvUsd,
                "liquidity" to liquidityFormatted,
                "holders" to holdersCount,
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
                "liquidity" to "Ошибка",
                "holders" to "Ошибка",
                "change_30m" to "0",
                "change_1h" to "0",
                "change_6h" to "0",
                "change_24h" to "0"
            )
        }
    }

    private fun updateUIFromCache(prefs: android.content.SharedPreferences) {
        priceTextView.text = prefs.getString("price_usd", "0") ?: "0"
        priceTonTextView.text = prefs.getString("price_ton", "0") ?: "0"
        fdvTextView.text = prefs.getString("fdv_usd", "0") ?: "0"
        liquidityTextView.text = prefs.getString("liquidity", "0") ?: "0"
        holdersTextView.text = prefs.getString("holders", "0") ?: "0"

        val change30m = prefs.getString("change_30m", "0")?.toDoubleOrNull() ?: 0.0
        val change1h = prefs.getString("change_1h", "0")?.toDoubleOrNull() ?: 0.0
        val change6h = prefs.getString("change_6h", "0")?.toDoubleOrNull() ?: 0.0
        val change24h = prefs.getString("change_24h", "0")?.toDoubleOrNull() ?: 0.0

        change30mTextView.text = formatPercentage(change30m)
        change1hTextView.text = formatPercentage(change1h)
        change6hTextView.text = formatPercentage(change6h)
        change24hTextView.text = formatPercentage(change24h)

        setTextColor(change30mTextView, change30m)
        setTextColor(change1hTextView, change1h)
        setTextColor(change6hTextView, change6h)
        setTextColor(change24hTextView, change24h)

        setBackgroundColor(change30mTextView, change30m)
        setBackgroundColor(change1hTextView, change1h)
        setBackgroundColor(change6hTextView, change6h)
        setBackgroundColor(change24hTextView, change24h)
    }

    private fun applyThemeFromPreferences() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkTheme) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
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

    private fun formatLiquidity(value: Double): String {
        val dfs = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = ' '; decimalSeparator = ',' }
        return DecimalFormat("$ #,##0.00", dfs).format(value)
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
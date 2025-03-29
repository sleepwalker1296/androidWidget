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
import java.text.DecimalFormatSymbols
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var priceTextView: TextView
    private lateinit var priceTonTextView: TextView
    private lateinit var fdvTextView: TextView
    private lateinit var liquidityTextView: TextView

    private lateinit var change30mTextView: TextView
    private lateinit var change1hTextView: TextView
    private lateinit var change6hTextView: TextView
    private lateinit var change24hTextView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 60_000L // Обновление каждую минуту
    private val poolUrls = listOf(
        "https://api.geckoterminal.com/api/v2/networks/ton/pools/EQAf2LUJZMdxSAGhlp-A60AN9bqZeVM994vCOXH05JFo-7dc",
        "https://api.geckoterminal.com/api/v2/networks/ton/pools/EQBJNKIIuskkvRxd5EHdfpNTtqbJoJWVbt7NI0WTeQ_2VJE3",
        "https://api.geckoterminal.com/api/v2/networks/ton/pools/EQBTFsnv95SKXSjTQV_uApIVNHqTL1Ye4CE42HPIcS7nszlO",
        "https://api.geckoterminal.com/api/v2/networks/ton/pools/EQAoJ9eh8MoKzErNE86N1uHzp4Eskth5Od5tDEYgS5mVU_Fj"
    )

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
        liquidityTextView = findViewById(R.id.liquidity)

        change30mTextView = findViewById(R.id.change_30m)
        change1hTextView = findViewById(R.id.change_1h)
        change6hTextView = findViewById(R.id.change_6h)
        change24hTextView = findViewById(R.id.change_24h)

        findViewById<Button>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

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
            var totalLiquidity = 0.0
            var priceUsd = "0"
            var priceTon = "0"
            var fdvUsd = "0.0"
            var change30m = "0.0"
            var change1h = "0.0"
            var change6h = "0.0"
            var change24h = "0.0"

            // Запрос данных из пулов GeckoTerminal
            for (url in poolUrls) {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e("MainActivity", "Ошибка ответа API для $url: ${response.code}")
                    continue
                }

                val responseBody = response.body?.string()
                Log.d("MainActivity", "API Response for $url: $responseBody")

                val json = JSONObject(responseBody ?: "{}")
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

            val liquidityFormatted = formatLiquidity(totalLiquidity)

            mapOf(
                "price_usd" to priceUsd,
                "price_ton" to priceTon,
                "fdv_usd" to fdvUsd,
                "liquidity" to liquidityFormatted,
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
                "change_30m" to "0",
                "change_1h" to "0",
                "change_6h" to "0",
                "change_24h" to "0"
            )
        }
    }

    private fun updateUI(data: Map<String, String>) {
        try {
            priceTextView.text = data["price_usd"]
            priceTonTextView.text = data["price_ton"]
            fdvTextView.text = data["fdv_usd"]
            liquidityTextView.text = data["liquidity"]

            val change30m = data["change_30m"]?.toDoubleOrNull() ?: 0.0
            val change1h = data["change_1h"]?.toDoubleOrNull() ?: 0.0
            val change6h = data["change_6h"]?.toDoubleOrNull() ?: 0.0
            val change24h = data["change_24h"]?.toDoubleOrNull() ?: 0.0

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

            Log.d("MainActivity", "UI обновлен: fdv=${data["fdv_usd"]}, liquidity=${data["liquidity"]}")
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

    private fun formatLiquidity(value: Double): String {
        val dfs = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        val df = DecimalFormat("$ #,##0.00", dfs)
        return df.format(value)
    }

    private fun formatPercentage(value: Double): String {
        return when {
            value > 0 -> "+${DecimalFormat("#0.0").format(value)}%"
            value < 0 -> "${DecimalFormat("#0.0").format(value)}%"
            else -> "0.0%"
        }
    }

    private fun setTextColor(textView: TextView, value: Double) {
        when {
            value > 0 -> textView.setTextColor(resources.getColor(android.R.color.holo_green_light, theme))
            value < 0 -> textView.setTextColor(resources.getColor(android.R.color.holo_red_light, theme))
            else -> textView.setTextColor(resources.getColor(android.R.color.black, theme))
        }
    }

    private fun setBackgroundColor(textView: TextView, value: Double) {
        val parentLayout = textView.parent as LinearLayout
        val transparentAlpha = 0x30

        when {
            value > 0 -> parentLayout.setBackgroundColor(
                android.graphics.Color.argb(transparentAlpha, 0, 255, 0)
            )
            value < 0 -> parentLayout.setBackgroundColor(
                android.graphics.Color.argb(transparentAlpha, 255, 0, 0)
            )
            else -> parentLayout.setBackgroundColor(
                android.graphics.Color.argb(transparentAlpha, 169, 169, 169)
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
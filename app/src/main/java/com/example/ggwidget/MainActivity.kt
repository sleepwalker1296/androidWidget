package com.example.ggwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
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
    private lateinit var marketCapTextView: TextView
    private lateinit var change1hTextView: TextView
    private lateinit var change6hTextView: TextView
    private lateinit var change12hTextView: TextView
    private lateinit var changeDayTextView: TextView
    private lateinit var webView: WebView

    private val jettonUrl = "https://api.dyor.io/v1/jettons/EQBlWgKnh_qbFYTXfKgGAQPxkxFsArDOSr9nlARSzydpNPwA"
    private val statsUrl = "https://api.dyor.io/v1/jettons/EQBlWgKnh_qbFYTXfKgGAQPxkxFsArDOSr9nlARSzydpNPwA/stats"
    private val apiKey = "eyJhbGciOiJIUzI1NiIsImtuIjowLCJ0eXAiOiJKV1QifQ.eyJkYXRhIjp7ImlkIjo0OCwidmVyc2lvbiI6Mn19.S3-WcrUehqy00pLsOhu81iwdirMJ7eYxBB6HgezzZmI"
    private val client = OkHttpClient()

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            fetchAndUpdateData()
            handler.postDelayed(this, 60_000)
        }
    }

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val isDarkTheme = result.data?.getBooleanExtra("dark_theme", false) ?: false
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            prefs.edit().putBoolean("dark_theme", isDarkTheme).apply()
            updateBackgroundAndChart(isDarkTheme)
        }
    }

    private val themeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "Received theme change broadcast")
            val isDarkTheme = intent?.getBooleanExtra("dark_theme", false) ?: false
            updateBackgroundAndChart(isDarkTheme)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkTheme) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация WebView
        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE // Отключаем кэш
        webView.setBackgroundColor(
            resources.getColor(if (isDarkTheme) android.R.color.black else android.R.color.white, theme)
        )
        webView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun setTheme(isDarkTheme: String) {}
        }, "AndroidInterface")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d("WebView", "Страница загружена: $url")
                view?.loadUrl("javascript:AndroidInterface.setTheme('$isDarkTheme')")
            }
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                Log.e("WebView", "Ошибка загрузки: ${error?.description}")
            }
        }
        webView.loadUrl("file:///android_asset/tradingview.html")

        Log.d("MainActivity", "onCreate: Current theme = $isDarkTheme")
        updateBackgroundAndChart(isDarkTheme)

        registerReceiver(
            themeChangeReceiver,
            IntentFilter("com.example.ggwidget.THEME_CHANGED"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

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

        val settingsButton = findViewById<ImageView>(R.id.settings_layout)
        settingsButton.setOnClickListener {
            val animation = AnimationUtils.loadAnimation(this, R.anim.rotate)
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                    settingsLauncher.launch(intent)
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })
            settingsButton.startAnimation(animation)
        }

        val pricePrefs = getSharedPreferences("price_cache", MODE_PRIVATE)
        if (savedInstanceState != null) {
            restoreFromSavedState(savedInstanceState)
        } else {
            updateUIFromCache(pricePrefs)
        }

        fetchAndUpdateData()
        handler.post(updateRunnable)
    }

    private fun updateBackgroundAndChart(isDarkTheme: Boolean) {
        val rootView = findViewById<LinearLayout>(R.id.main_layout)
        rootView?.let {
            if (isDarkTheme) {
                it.setBackgroundColor(resources.getColor(android.R.color.black, theme))
                webView.setBackgroundColor(resources.getColor(android.R.color.black, theme))
            } else {
                it.setBackgroundColor(resources.getColor(android.R.color.white, theme))
                webView.setBackgroundColor(resources.getColor(android.R.color.white, theme))
            }
            Log.d("MainActivity", "Background updated to $isDarkTheme")
        }
        // Принудительное обновление темы с задержкой
        handler.postDelayed({
            webView.evaluateJavascript("AndroidInterface.setTheme('$isDarkTheme')") { result ->
                Log.d("WebView", "Результат вызова setTheme: $result")
            }
        }, 200)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        unregisterReceiver(themeChangeReceiver)
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)
        Log.d("MainActivity", "onResume: Current theme = $isDarkTheme")
        updateBackgroundAndChart(isDarkTheme)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("price_usd", priceTextView.text.toString())
        outState.putString("price_ton", priceTonTextView.text.toString())
        outState.putString("fdv_usd", fdvTextView.text.toString())
        outState.putString("liquidity", liquidityTextView.text.toString())
        outState.putString("holders", holdersTextView.text.toString())
        outState.putString("market_cap", marketCapTextView.text.toString())
        outState.putString("change_1h", change1hTextView.text.toString())
        outState.putString("change_6h", change6hTextView.text.toString())
        outState.putString("change_12h", change12hTextView.text.toString())
        outState.putString("change_day", changeDayTextView.text.toString())
    }

    private fun restoreFromSavedState(savedInstanceState: Bundle) {
        priceTextView.text = savedInstanceState.getString("price_usd", "$ 12,34")
        priceTonTextView.text = savedInstanceState.getString("price_ton", "11,23")
        fdvTextView.text = savedInstanceState.getString("fdv_usd", "100B")
        liquidityTextView.text = savedInstanceState.getString("liquidity", "5M")
        holdersTextView.text = savedInstanceState.getString("holders", "0")
        marketCapTextView.text = savedInstanceState.getString("market_cap", "777 777")

        val change1h = savedInstanceState.getString("change_1h", "0.0%")?.let { parsePercentage(it) } ?: 0.0
        val change6h = savedInstanceState.getString("change_6h", "0.0%")?.let { parsePercentage(it) } ?: 0.0
        val change12h = savedInstanceState.getString("change_12h", "0.0%")?.let { parsePercentage(it) } ?: 0.0
        val changeDay = savedInstanceState.getString("change_day", "0.0%")?.let { parsePercentage(it) } ?: 0.0

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

    private fun fetchAndUpdateData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val data = fetchDataFromAPI()
            Log.d("MainActivity", "Fetched data: $data")
            val prefs = getSharedPreferences("price_cache", MODE_PRIVATE)
            with(prefs.edit()) {
                data.forEach { (key, value) -> putString(key, value) }
                apply()
            }
            if (!isFinishing && !isDestroyed) {
                runOnUiThread { updateUIFromCache(prefs) }
            }
        }
    }

    private fun fetchDataFromAPI(): Map<String, String> {
        try {
            val jettonRequest = Request.Builder()
                .url(jettonUrl)
                .header("accept", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .build()
            val jettonResponse = client.newCall(jettonRequest).execute()
            val jettonBody = jettonResponse.body?.string() ?: "{}"
            Log.d("MainActivity", "Jetton API response: $jettonBody")

            val jettonData = when (jettonResponse.code) {
                429 -> {
                    Log.w("MainActivity", "Jetton API: Too many requests")
                    return getCachedOrDefaultData()
                }
                401 -> {
                    Log.w("MainActivity", "Jetton API: Unauthorized")
                    mapOf(
                        "price_usd" to "Ошибка авторизации",
                        "price" to "Ошибка авторизации",
                        "fdv_usd" to "Ошибка авторизации",
                        "liquidityUsd" to "Ошибка авторизации",
                        "holders" to "Ошибка авторизации",
                        "market_cap" to "Ошибка авторизации"
                    )
                }
                else -> {
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
            }

            val statsRequest = Request.Builder()
                .url(statsUrl)
                .header("accept", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .build()
            val statsResponse = client.newCall(statsRequest).execute()
            val statsBody = statsResponse.body?.string() ?: "{}"
            Log.d("MainActivity", "Stats API response: $statsBody")

            val statsData = when (statsResponse.code) {
                429 -> {
                    Log.w("MainActivity", "Stats API: Too many requests")
                    mapOf("change_1h" to "0", "change_6h" to "0", "change_12h" to "0", "change_day" to "0")
                }
                401 -> {
                    Log.w("MainActivity", "Stats API: Unauthorized")
                    mapOf("change_1h" to "0", "change_6h" to "0", "change_12h" to "0", "change_day" to "0")
                }
                else -> {
                    val statsJson = JSONObject(statsBody)
                    val priceChangeJson = statsJson.optJSONObject("priceChange")?.optJSONObject("ton") ?: JSONObject()

                    mapOf(
                        "change_1h" to (priceChangeJson.optJSONObject("hour")?.optDouble("changePercent", 0.0) ?: 0.0).toString(),
                        "change_6h" to (priceChangeJson.optJSONObject("hour6")?.optDouble("changePercent", 0.0) ?: 0.0).toString(),
                        "change_12h" to (priceChangeJson.optJSONObject("hour12")?.optDouble("changePercent", 0.0) ?: 0.0).toString(),
                        "change_day" to (priceChangeJson.optJSONObject("day")?.optDouble("changePercent", 0.0) ?: 0.0).toString()
                    )
                }
            }

            return jettonData + statsData
        } catch (e: Exception) {
            Log.e("MainActivity", "Ошибка при запросе данных", e)
            return getCachedOrDefaultData()
        }
    }

    private fun getCachedOrDefaultData(): Map<String, String> {
        val prefs = getSharedPreferences("price_cache", MODE_PRIVATE)
        return mapOf(
            "price_usd" to (prefs.getString("price_usd", "$ 12,34") ?: "$ 12,34"),
            "price" to (prefs.getString("price", "11,23") ?: "11,23"),
            "fdv_usd" to (prefs.getString("fdv_usd", "100B") ?: "100B"),
            "liquidityUsd" to (prefs.getString("liquidityUsd", "5M") ?: "5M"),
            "holders" to (prefs.getString("holders", "0") ?: "0"),
            "market_cap" to (prefs.getString("market_cap", "777 777") ?: "777 777"),
            "change_1h" to (prefs.getString("change_1h", "0") ?: "0"),
            "change_6h" to (prefs.getString("change_6h", "0") ?: "0"),
            "change_12h" to (prefs.getString("change_12h", "0") ?: "0"),
            "change_day" to (prefs.getString("change_day", "0") ?: "0")
        )
    }

    private fun updateUIFromCache(prefs: android.content.SharedPreferences) {
        priceTextView.text = prefs.getString("price_usd", "$ 12,34")
        priceTonTextView.text = prefs.getString("price", "11,23")
        fdvTextView.text = prefs.getString("fdv_usd", "100B")
        liquidityTextView.text = prefs.getString("liquidityUsd", "5M")
        holdersTextView.text = prefs.getString("holders", "0")
        marketCapTextView.text = prefs.getString("market_cap", "777 777")

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

    private fun formatPrice(price: Double): String {
        return try {
            "$" + DecimalFormat("#0.00").format(price).replace(",", ".")
        } catch (e: Exception) {
            "$ 12,34"
        }
    }

    private fun formatTonPrice(price: Double): String {
        return try {
            DecimalFormat("#0.00").format(price).replace(",", ".")
        } catch (e: Exception) {
            "11,23"
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
            "100B"
        }
    }

    private fun formatLiquidity(value: Double): String {
        return try {
            val dfs = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = ' '; decimalSeparator = ',' }
            DecimalFormat("$ #,##0.00", dfs).format(value)
        } catch (e: Exception) {
            "5M"
        }
    }

    private fun formatSupply(value: Double): String {
        return try {
            DecimalFormat("#,##0").format(value).replace(",", " ")
        } catch (e: Exception) {
            "777 777"
        }
    }

    private fun formatPercentage(value: Double): String {
        return when {
            value > 0 -> "+${DecimalFormat("#0.0").format(value)}%"
            value < 0 -> "${DecimalFormat("#0.0").format(value)}%"
            else -> "0.0%"
        }
    }

    private fun parsePercentage(text: String): Double {
        return text.replace("[^0-9.-]".toRegex(), "").toDoubleOrNull() ?: 0.0
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
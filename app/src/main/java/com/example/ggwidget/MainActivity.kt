package com.example.ggwidget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {

    private lateinit var priceTextView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 60_000L

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isScreenOn()) {
                fetchCoinPrice()
            }
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        priceTextView = findViewById(R.id.price_text)

        fetchCoinPrice()

        val settingsButton: Button = findViewById(R.id.settings_button)
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivityForResult(intent, SETTINGS_REQUEST_CODE)
        }
    }

    override fun onResume() {
        super.onResume()
        fetchCoinPrice()
        handler.postDelayed(updateRunnable, updateInterval)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK) {
            // Полностью перезапускаем активность при возврате из настроек
            finish()
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun fetchCoinPrice() {
        CoroutineScope(Dispatchers.IO).launch {
            val price = fetchDataFromInternet()
            runOnUiThread {
                priceTextView.text = price
            }
        }
    }

    private fun fetchDataFromInternet(): String {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.geckoterminal.com/api/v2/networks/ton/pools/EQAoJ9eh8MoKzErNE86N1uHzp4Eskth5Od5tDEYgS5mVU_Fj")
                .build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            val price = json.getJSONObject("data")
                .getJSONObject("attributes")
                .getString("base_token_price_usd")

            formatPrice(price)
        } catch (e: Exception) {
            "Ошибка сети"
        }
    }

    private fun formatPrice(price: String): String {
        return try {
            val number = price.toDouble()
            val decimalFormat = DecimalFormat("#0.00")
            "$" + decimalFormat.format(number).replace(",", ".")
        } catch (e: Exception) {
            "Ошибка"
        }
    }

    private fun isScreenOn(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    companion object {
        private const val SETTINGS_REQUEST_CODE = 1001
    }
}
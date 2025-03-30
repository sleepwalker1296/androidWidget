package com.example.ggwidget.utils

import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object NetworkUtils {
    private const val API_URL = "https://api.dyor.io/v1/jettons/EQBlWgKnh_qbFYTXfKgGAQPxkxFsArDOSr9nlARSzydpNPwA"

    fun fetchCoinPrice(callback: (String) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(API_URL)
            .header("accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Ошибка сети")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { jsonString ->
                    try {
                        val json = JSONObject(jsonString)
                        val priceUsdValue = json.optJSONObject("details")
                            ?.optJSONObject("priceUsd")
                            ?.optString("value", "0") ?: "0"
                        val decimals = json.optJSONObject("details")
                            ?.optJSONObject("priceUsd")
                            ?.optInt("decimals", 6) ?: 6
                        val priceUsd = priceUsdValue.toDouble() / Math.pow(10.0, decimals.toDouble())
                        callback("$${"%.2f".format(priceUsd)}")
                    } catch (e: Exception) {
                        callback("Ошибка данных")
                    }
                } ?: callback("Ошибка сети")
            }
        })
    }
}
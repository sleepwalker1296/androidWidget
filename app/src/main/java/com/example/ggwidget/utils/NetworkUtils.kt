package com.example.ggwidget.utils

import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object NetworkUtils {
    private const val API_URL = "https://api.geckoterminal.com/api/v2/networks/ton/pools/EQAoJ9eh8MoKzErNE86N1uHzp4Eskth5Od5tDEYgS5mVU_Fj"

    fun fetchCoinPrice(callback: (String) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder().url(API_URL).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Ошибка сети")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { jsonString ->
                    try {
                        val json = JSONObject(jsonString)
                        val price = json.getJSONObject("data")
                            .getJSONObject("attributes")
                            .getJSONObject("reserve_in_usd")
                            .getString("price")

                        callback("$${"%.2f".format(price.toFloat())}")
                    } catch (e: Exception) {
                        callback("Ошибка данных")
                    }
                } ?: callback("Ошибка сети")
            }
        })
    }
}

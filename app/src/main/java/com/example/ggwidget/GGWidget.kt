package com.example.ggwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class GGWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        CoroutineScope(Dispatchers.IO).launch {
            val price = fetchDataFromInternet()
            withContext(Dispatchers.Main) {
                views.setTextViewText(R.id.widget_text, "#GOVNO: $price")
                appWidgetManager.updateAppWidget(appWidgetId, views)
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

            "$${"%.2f".format(price.toFloat())}" // Форматируем цену до 2 знаков после запятой
        } catch (e: Exception) {
            "Ошибка сети"
        }
    }
}

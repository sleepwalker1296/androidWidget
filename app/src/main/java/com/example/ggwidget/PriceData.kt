package com.example.ggwidget.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_data")
data class PriceData(
    @PrimaryKey val id: Int = 1, // Используем один ID, так как данные единичные
    val priceUsd: String,
    val priceTon: String,
    val fdvUsd: String,
    val liquidity: String,
    val holders: String,
    val change30m: String,
    val change1h: String,
    val change6h: String,
    val change24h: String,
    val timestamp: Long = System.currentTimeMillis() // Время последнего обновления
)
package com.example.ggwidget.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceDao {
    @Query("SELECT * FROM price_data WHERE id = 1")
    fun getPriceData(): Flow<PriceData?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(priceData: PriceData)
}
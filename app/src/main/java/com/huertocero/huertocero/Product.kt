package com.huertocero.huertocero

import com.google.firebase.Timestamp

data class Product(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var price: Any = "",
    var lat: Double? = null,
    var lng: Double? = null,
    var imageUrl: String = "",
    var sellerId: String = "",
    var category: String = "Otros",
    var stockTotal: Any = 1.0,
    var stockReserved: Any = 0.0,
    var unit: String = "kg",
    var currency: String = "EUR",
    var geoCell: String = "",
    var publishedDateKey: String = "",
    var createdAt: Timestamp? = null,
    var viewCount: Any = 0,
    var favoriteCount: Any = 0,
    var reservationCount: Any = 0
) {
    fun getPriceAsDouble(): Double {
        return when (price) {
            is Double -> price as Double
            is Long -> (price as Long).toDouble()
            is String -> (price as String).toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    fun getStockTotalAsDouble(): Double = valueAsDouble(stockTotal, 1.0)

    fun getStockReservedAsDouble(): Double = valueAsDouble(stockReserved, 0.0)

    fun getAvailableStock(): Double = (getStockTotalAsDouble() - getStockReservedAsDouble()).coerceAtLeast(0.0)

    fun normalizedUnit(): String = MarketFormat.normalizeUnit(unit)

    fun normalizedCurrency(): String = MarketFormat.normalizeCurrency(currency)

    fun getFavoriteCountAsLong(): Long = valueAsLong(favoriteCount)

    fun getReservationCountAsLong(): Long = valueAsLong(reservationCount)

    private fun valueAsDouble(value: Any, fallback: Double): Double {
        return when (value) {
            is Double -> value
            is Long -> value.toDouble()
            is Int -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: fallback
            else -> fallback
        }
    }

    private fun valueAsLong(value: Any): Long {
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }
}

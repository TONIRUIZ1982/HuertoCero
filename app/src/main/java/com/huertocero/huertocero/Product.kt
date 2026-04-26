package com.huertocero.huertocero

data class Product(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var price: Any = "",
    var lat: Double? = null,
    var lng: Double? = null,
    var imageUrl: String = "",
    var sellerId: String = ""
) {
    fun getPriceAsDouble(): Double {
        return when (price) {
            is Double -> price as Double
            is String -> (price as String).toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }
}

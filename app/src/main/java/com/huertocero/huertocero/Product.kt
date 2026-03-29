package com.huertocero.huertocero

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

data class Product(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var price: Any = 0.0,
    var lat: Double? = null,
    var lng: Double? = null,
    var imageUrl: String = ""
) : ClusterItem {

    override fun getPosition(): LatLng {
        return LatLng(lat ?: 0.0, lng ?: 0.0)
    }

    override fun getTitle(): String {
        return if (name.isNotEmpty()) name else "Producto"
    }

    override fun getSnippet(): String {
        return if (description.isNotEmpty()) description else "Sin descripción"
    }

    // 🔥 FIX IMPORTANTE AQUÍ
    fun getPriceAsDouble(): Double {

        val value = price   // 👈 CLAVE: variable local

        return when (value) {
            is Double -> value
            is Long -> value.toDouble()
            is Int -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    fun getFormattedPrice(): String {
        return "€${getPriceAsDouble()}"
    }

    fun hasValidLocation(): Boolean {
        return lat != null && lng != null
    }
}
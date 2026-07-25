package com.huertocero.huertocero

import kotlin.math.round

object MarketplaceSignals {
    const val SELLER_INDIVIDUAL = "individual"
    const val SELLER_SHOP = "shop"
    const val SELLER_FARMER = "farmer"

    const val FULFILLMENT_PICKUP = "pickup"
    const val FULFILLMENT_LOCAL_DELIVERY = "local_delivery"
    const val FULFILLMENT_PICKUP_DELIVERY = "pickup_delivery"

    val sellerTypes = listOf(SELLER_INDIVIDUAL, SELLER_SHOP, SELLER_FARMER)
    val fulfillmentModes = listOf(FULFILLMENT_PICKUP, FULFILLMENT_LOCAL_DELIVERY, FULFILLMENT_PICKUP_DELIVERY)

    fun normalizeSellerType(value: String): String {
        return sellerTypes.firstOrNull { it.equals(value, ignoreCase = true) } ?: SELLER_INDIVIDUAL
    }

    fun normalizeFulfillmentMode(value: String): String {
        return fulfillmentModes.firstOrNull { it.equals(value, ignoreCase = true) } ?: FULFILLMENT_PICKUP
    }

    fun sellerTypeLabelRes(value: String): Int {
        return when (normalizeSellerType(value)) {
            SELLER_SHOP -> R.string.seller_type_shop
            SELLER_FARMER -> R.string.seller_type_farmer
            else -> R.string.seller_type_individual
        }
    }

    fun sellerTypeBadgeRes(value: String): Int {
        return when (normalizeSellerType(value)) {
            SELLER_SHOP -> R.string.seller_badge_shop
            SELLER_FARMER -> R.string.seller_badge_farmer
            else -> R.string.seller_badge_individual
        }
    }

    fun fulfillmentLabelRes(value: String): Int {
        return when (normalizeFulfillmentMode(value)) {
            FULFILLMENT_LOCAL_DELIVERY -> R.string.fulfillment_local_delivery
            FULFILLMENT_PICKUP_DELIVERY -> R.string.fulfillment_pickup_delivery
            else -> R.string.fulfillment_pickup
        }
    }

    fun fulfillmentBadgeRes(value: String): Int {
        return when (normalizeFulfillmentMode(value)) {
            FULFILLMENT_LOCAL_DELIVERY -> R.string.delivery_badge
            FULFILLMENT_PICKUP_DELIVERY -> R.string.pickup_delivery_badge
            else -> R.string.pickup_badge
        }
    }

    fun suggestPrice(category: String, unit: String, stock: Double): Double {
        val normalizedCategory = ProductCategories.normalize(category)
        val normalizedUnit = MarketFormat.normalizeUnit(unit)
        val base = when (normalizedCategory) {
            "Frutas" -> 2.80
            "Verduras" -> 2.40
            "Carniceria" -> 12.00
            "Pescaderia" -> 14.00
            "Panaderia" -> 3.20
            "Lacteos" -> 5.00
            "Despensa" -> 4.50
            "Comida preparada" -> 7.00
            "Huevos" -> 0.35
            "Miel" -> 8.50
            "Aceite" -> 7.50
            "Plantas" -> 4.00
            else -> 3.00
        }

        val unitFactor = when (normalizedUnit) {
            "g" -> 0.10
            "lb" -> 0.45
            "unit" -> if (normalizedCategory in listOf("Huevos", "Panaderia", "Plantas")) 1.0 else 0.65
            "box" -> if (normalizedCategory == "Huevos") 12.0 else 3.0
            "bunch" -> 0.75
            "L" -> if (normalizedCategory == "Aceite") 1.0 else 0.9
            else -> 1.0
        }

        val scarcityFactor = if (stock in 0.1..2.0) 1.12 else 1.0
        return round(base * unitFactor * scarcityFactor * 100.0) / 100.0
    }
}

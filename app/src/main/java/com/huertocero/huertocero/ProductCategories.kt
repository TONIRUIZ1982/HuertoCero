package com.huertocero.huertocero

object ProductCategories {
    const val FILTER_ALL = "Todos"
    const val OTHER = "Otros"

    val all = listOf(
        "Frutas",
        "Verduras",
        "Carniceria",
        "Pescaderia",
        "Panaderia",
        "Lacteos",
        "Despensa",
        "Comida preparada",
        "Huevos",
        "Miel",
        "Aceite",
        "Plantas",
        OTHER
    )
    val filters = listOf(FILTER_ALL) + all

    fun normalize(category: String): String {
        return all.firstOrNull { it.equals(category, ignoreCase = true) } ?: OTHER
    }

    fun labelRes(category: String): Int {
        return when (normalize(category)) {
            "Frutas" -> R.string.category_fruits
            "Verduras" -> R.string.category_vegetables
            "Carniceria" -> R.string.category_butcher
            "Pescaderia" -> R.string.category_fishmonger
            "Panaderia" -> R.string.category_bakery
            "Lacteos" -> R.string.category_dairy
            "Despensa" -> R.string.category_grocery
            "Comida preparada" -> R.string.category_prepared_food
            "Huevos" -> R.string.category_eggs
            "Miel" -> R.string.category_honey
            "Aceite" -> R.string.category_oil
            "Plantas" -> R.string.category_plants
            else -> R.string.category_other
        }
    }
}

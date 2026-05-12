package com.huertocero.huertocero

import android.content.Context
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

object MarketFormat {
    val currencies = listOf("EUR", "USD", "GBP", "JPY", "CNY", "INR", "BRL", "MXN", "CAD", "AUD")
    val units = listOf("kg", "g", "lb", "unit", "box", "bunch", "L")

    fun normalizeCurrency(currency: String): String {
        return currency.uppercase(Locale.US).takeIf { it in currencies } ?: "EUR"
    }

    fun normalizeUnit(unit: String): String {
        return units.firstOrNull { it.equals(unit, ignoreCase = true) } ?: "kg"
    }

    fun formatMoney(context: Context, amount: Double, currencyCode: String): String {
        val locale = LocaleHelper.getLocale(context)
        return runCatching {
            NumberFormat.getCurrencyInstance(locale).apply {
                currency = Currency.getInstance(normalizeCurrency(currencyCode))
                maximumFractionDigits = if (currency?.defaultFractionDigits == 0) 0 else 2
            }.format(amount)
        }.getOrElse {
            "${trim(amount)} ${normalizeCurrency(currencyCode)}"
        }
    }

    fun formatQuantity(context: Context, amount: Double, unit: String): String {
        val formatted = trim(amount)
        val unitLabel = when (normalizeUnit(unit)) {
            "unit" -> context.getString(R.string.unit_piece)
            "box" -> context.getString(R.string.unit_box)
            "bunch" -> context.getString(R.string.unit_bunch)
            else -> normalizeUnit(unit)
        }
        return "$formatted $unitLabel"
    }

    private fun trim(value: Double): String {
        return if (value == value.roundToInt().toDouble()) {
            value.roundToInt().toString()
        } else {
            "%.2f".format(Locale.US, value).trimEnd('0').trimEnd('.')
        }
    }
}

package com.huertocero.huertocero

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    data class SupportedLanguage(val code: String, val displayName: String)

    val supportedLanguages = listOf(
        SupportedLanguage("es", "Español"),
        SupportedLanguage("en", "English"),
        SupportedLanguage("fr", "Français"),
        SupportedLanguage("de", "Deutsch"),
        SupportedLanguage("it", "Italiano"),
        SupportedLanguage("pt", "Português"),
        SupportedLanguage("zh", "中文"),
        SupportedLanguage("ja", "日本語"),
        SupportedLanguage("ko", "한국어"),
        SupportedLanguage("hi", "हिन्दी"),
        SupportedLanguage("ar", "العربية"),
        SupportedLanguage("id", "Bahasa Indonesia"),
        SupportedLanguage("tr", "Türkçe")
    )

    fun wrap(context: Context): Context {
        val locale = getLocale(context)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    fun setLanguage(context: Context, language: String) {
        // HuertoCero follows the device language automatically.
    }

    fun getLanguage(context: Context): String {
        val deviceLanguage = Locale.getDefault().language
        return supportedLanguages.firstOrNull { it.code == deviceLanguage }?.code ?: "en"
    }

    fun getLocale(context: Context): Locale {
        return Locale.forLanguageTag(getLanguage(context))
    }
}

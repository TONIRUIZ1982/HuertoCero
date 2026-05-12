package com.huertocero.huertocero

import androidx.appcompat.app.AlertDialog

object LanguageDialog {
    fun show(activity: HuertoActivity) {
        val languages = LocaleHelper.supportedLanguages.map { it.displayName }.toTypedArray()
        val codes = LocaleHelper.supportedLanguages.map { it.code }.toTypedArray()
        val current = LocaleHelper.getLanguage(activity)
        val checked = codes.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.language_title))
            .setSingleChoiceItems(languages, checked) { dialog, which ->
                LocaleHelper.setLanguage(activity, codes[which])
                dialog.dismiss()
                activity.recreate()
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }
}

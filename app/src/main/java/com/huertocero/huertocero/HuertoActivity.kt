package com.huertocero.huertocero

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

open class HuertoActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}

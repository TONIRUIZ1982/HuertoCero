package com.huertocero.huertocero

import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class PrivacyActivity : HuertoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvPrivacyBody).text = getString(R.string.privacy_body)
    }
}

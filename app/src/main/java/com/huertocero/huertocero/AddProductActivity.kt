package com.huertocero.huertocero

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore

class AddProductActivity : HuertoActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_product)

        val name = findViewById<EditText>(R.id.etName)
        val desc = findViewById<EditText>(R.id.etDescription)
        val price = findViewById<EditText>(R.id.etPrice)
        val btnSave = findViewById<Button>(R.id.btnSave)

        val lat = intent.getDoubleExtra("lat", 0.0)
        val lng = intent.getDoubleExtra("lng", 0.0)

        btnSave.setOnClickListener {
            val product = Product(
                name = name.text.toString(),
                description = desc.text.toString(),
                price = price.text.toString().toDoubleOrNull() ?: 0.0,
                lat = lat,
                lng = lng,
                imageUrl = ""
            )

            db.collection("products").add(product)
                .addOnSuccessListener {
                    Toast.makeText(this, getString(R.string.product_saved), Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }
}

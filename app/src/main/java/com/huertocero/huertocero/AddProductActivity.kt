package com.huertocero.huertocero

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class AddProductActivity : AppCompatActivity() {

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
                imageUrl = "" // de momento vacío
            )

            db.collection("products").add(product)
                .addOnSuccessListener {
                    Toast.makeText(this, "Producto guardado", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }
}
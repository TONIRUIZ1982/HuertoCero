package com.huertocero.huertocero

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
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
            val productName = name.text.toString().trim()
            val description = desc.text.toString().trim()
            val productPrice = price.text.toString().trim().replace(',', '.').toDoubleOrNull() ?: 0.0
            val sellerId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            if (productName.isBlank()) {
                Toast.makeText(this, getString(R.string.fill_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val product = Product(
                name = productName,
                description = description,
                price = productPrice,
                category = ProductCategories.OTHER,
                sellerId = sellerId,
                stockTotal = 1.0,
                stockReserved = 0.0,
                unit = MarketFormat.normalizeUnit("kg"),
                currency = MarketFormat.normalizeCurrency("EUR"),
                sellerType = MarketplaceSignals.SELLER_INDIVIDUAL,
                fulfillmentMode = MarketplaceSignals.FULFILLMENT_PICKUP,
                isEcoLocal = true,
                suggestedPrice = MarketplaceSignals.suggestPrice(ProductCategories.OTHER, "kg", 1.0),
                deliveryRadiusKm = 5.0,
                deliveryFee = 0.0,
                lat = lat,
                lng = lng,
                geoCell = GeoEngagement.geoCell(lat, lng),
                publishedDateKey = GeoEngagement.todayKey(),
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

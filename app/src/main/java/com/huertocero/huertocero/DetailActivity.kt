package com.huertocero.huertocero

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

class DetailActivity : HuertoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val img = findViewById<ImageView>(R.id.imgProduct)
        val tvName = findViewById<TextView>(R.id.tvName)
        val tvDesc = findViewById<TextView>(R.id.tvDescription)
        val tvPrice = findViewById<TextView>(R.id.tvPrice)
        val btnNav = findViewById<Button>(R.id.btnNavigate)
        val btnReserve = findViewById<Button>(R.id.btnReserve)

        val id = intent.getStringExtra("productId") ?: return

        FirebaseFirestore.getInstance()
            .collection("products")
            .document(id)
            .get()
            .addOnSuccessListener {
                val product = it.toObject(Product::class.java) ?: return@addOnSuccessListener

                tvName.text = product.name
                tvDesc.text = product.description
                tvPrice.text = "${product.getPriceAsDouble()} EUR"

                Glide.with(this)
                    .load(product.imageUrl.ifEmpty { "https://via.placeholder.com/300" })
                    .into(img)

                btnNav.setOnClickListener {
                    val uri = Uri.parse("google.navigation:q=${product.lat},${product.lng}")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    intent.setPackage("com.google.android.apps.maps")
                    startActivity(intent)
                }

                btnReserve.setOnClickListener {
                    showReserveDialog(product)
                }
            }
    }

    private fun showReserveDialog(product: Product) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val info = TextView(this).apply {
            text = getString(
                R.string.description_price,
                product.description,
                product.getPriceAsDouble().toString()
            )
        }

        val input = EditText(this).apply {
            hint = getString(R.string.quantity)
        }

        layout.addView(info)
        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle(product.name)
            .setView(layout)
            .setPositiveButton(getString(R.string.reserve)) { _, _ ->
                val data = hashMapOf(
                    "productId" to product.id,
                    "nombre" to product.name,
                    "precio" to product.getPriceAsDouble()
                )

                FirebaseFirestore.getInstance()
                    .collection("reservas")
                    .add(data)

                Toast.makeText(this, getString(R.string.product_reserved), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}

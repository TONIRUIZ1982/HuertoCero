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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DetailActivity : HuertoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val img = findViewById<ImageView>(R.id.imgProduct)
        val tvName = findViewById<TextView>(R.id.tvName)
        val tvDesc = findViewById<TextView>(R.id.tvDescription)
        val tvPrice = findViewById<TextView>(R.id.tvPrice)
        val tvStock = findViewById<TextView>(R.id.tvStock)
        val btnNav = findViewById<Button>(R.id.btnNavigate)
        val btnReserve = findViewById<Button>(R.id.btnReserve)
        UiMotion.makePressable(btnNav, btnReserve)

        val id = intent.getStringExtra("productId") ?: return

        FirebaseFirestore.getInstance()
            .collection("products")
            .document(id)
            .get()
            .addOnSuccessListener {
                val product = it.toObject(Product::class.java) ?: return@addOnSuccessListener
                product.id = it.id

                tvName.text = product.name
                tvDesc.text = product.description
                tvPrice.text = MarketFormat.formatMoney(this, product.getPriceAsDouble(), product.normalizedCurrency())
                val available = MarketFormat.formatQuantity(this, product.getAvailableStock(), product.normalizedUnit())
                tvStock.text = if (product.getAvailableStock() <= 2.0) {
                    getString(R.string.only_left, available)
                } else {
                    getString(R.string.available_stock, available)
                }
                UiMotion.reveal(img, tvName, tvPrice, tvStock, tvDesc, btnNav, btnReserve)

                Glide.with(this)
                    .load(product.imageUrl.ifEmpty { "https://via.placeholder.com/300" })
                    .into(img)

                btnNav.setOnClickListener {
                    openNavigationToProduct(product)
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
                MarketFormat.formatMoney(this@DetailActivity, product.getPriceAsDouble(), product.normalizedCurrency())
            )
        }

        val input = EditText(this).apply {
            hint = getString(R.string.quantity_hint)
        }

        layout.addView(info)
        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle(product.name)
            .setView(layout)
            .setPositiveButton(getString(R.string.reserve)) { _, _ ->
                val buyerId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton
                val quantity = input.text.toString().toDoubleOrNull()
                if (quantity == null || quantity <= 0.0) {
                    Toast.makeText(this, getString(R.string.reservation_error_quantity), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                ReservationService.reserveProduct(
                    FirebaseFirestore.getInstance(),
                    product,
                    buyerId,
                    quantity
                ).addOnSuccessListener {
                    Toast.makeText(this, getString(R.string.product_reserved), Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(this, getString(R.string.reservation_error_generic), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openNavigationToProduct(product: Product) {
        val lat = product.lat
        val lng = product.lng

        if (lat == null || lng == null) {
            Toast.makeText(this, getString(R.string.location_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=w")).apply {
            setPackage("com.google.android.apps.maps")
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")))
        }
    }
}

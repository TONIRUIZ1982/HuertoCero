package com.huertocero.huertocero

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
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
import java.util.Locale

class DetailActivity : HuertoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val img = findViewById<ImageView>(R.id.imgProduct)
        val tvName = findViewById<TextView>(R.id.tvName)
        val tvDesc = findViewById<TextView>(R.id.tvDescription)
        val tvPrice = findViewById<TextView>(R.id.tvPrice)
        val tvStock = findViewById<TextView>(R.id.tvStock)
        val tvSellerBadge = findViewById<TextView>(R.id.tvSellerBadge)
        val tvFulfillmentBadge = findViewById<TextView>(R.id.tvFulfillmentBadge)
        val tvEcoBadge = findViewById<TextView>(R.id.tvEcoBadge)
        val tvReserveNotice = findViewById<TextView>(R.id.tvReserveNotice)
        val tvDeliveryInfo = findViewById<TextView>(R.id.tvDeliveryInfo)
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
                tvDesc.text = product.description.ifBlank { getString(R.string.no_description) }
                tvPrice.text = MarketFormat.formatMoney(this, product.getPriceAsDouble(), product.normalizedCurrency())
                val available = MarketFormat.formatQuantity(this, product.getAvailableStock(), product.normalizedUnit())
                tvStock.text = if (product.getAvailableStock() <= 2.0) {
                    getString(R.string.only_left, available)
                } else {
                    getString(R.string.available_stock, available)
                }
                tvSellerBadge.text = getString(MarketplaceSignals.sellerTypeBadgeRes(product.normalizedSellerType()))
                tvFulfillmentBadge.text = getString(MarketplaceSignals.fulfillmentBadgeRes(product.normalizedFulfillmentMode()))
                tvEcoBadge.text = if (product.isEcoLocal) getString(R.string.badge_km0) else getString(R.string.badge_local)
                tvReserveNotice.text = getString(R.string.reservation_hold_notice)
                tvDeliveryInfo.text = buildDeliveryInfo(product)
                UiMotion.reveal(
                    img,
                    tvName,
                    tvPrice,
                    tvStock,
                    tvSellerBadge,
                    tvFulfillmentBadge,
                    tvEcoBadge,
                    tvDesc,
                    tvReserveNotice,
                    tvDeliveryInfo,
                    btnNav,
                    btnReserve
                )
                EngagementTracker.productEvent("view_detail", product)

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

    private fun buildDeliveryInfo(product: Product): String {
        if (!product.hasLocalDelivery()) {
            return getString(R.string.pickup_details)
        }

        val radius = formatRadius(product.getDeliveryRadiusKmAsDouble())
        val fee = product.getDeliveryFeeAsDouble()
        return if (fee <= 0.0) {
            getString(R.string.delivery_details_free, radius)
        } else {
            getString(
                R.string.delivery_details_fee,
                radius,
                MarketFormat.formatMoney(this, fee, product.normalizedCurrency())
            )
        }
    }

    private fun formatRadius(value: Double): String {
        return if (value == value.toInt().toDouble()) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    private fun showReserveDialog(product: Product) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val info = TextView(this).apply {
            text = getString(
                R.string.description_price,
                product.description.ifBlank { getString(R.string.no_description) },
                MarketFormat.formatMoney(this@DetailActivity, product.getPriceAsDouble(), product.normalizedCurrency())
            )
        }

        val input = EditText(this).apply {
            hint = getString(R.string.quantity_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        layout.addView(info)
        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle(product.name)
            .setView(layout)
            .setPositiveButton(getString(R.string.reserve)) { _, _ ->
                val buyerId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton
                val quantity = input.text.toString().trim().replace(',', '.').toDoubleOrNull()
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
                    EngagementTracker.productEvent("reserve_detail", product)
                    Toast.makeText(
                        this,
                        "${getString(R.string.product_reserved)}. ${getString(R.string.reservation_hold_notice)}",
                        Toast.LENGTH_LONG
                    ).show()
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

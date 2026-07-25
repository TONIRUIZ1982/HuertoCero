package com.huertocero.huertocero

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

class SellerProfileActivity : HuertoActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val products = mutableListOf<Product>()
    private lateinit var adapter: BaseAdapter
    private lateinit var sellerName: TextView
    private lateinit var stats: TextView
    private lateinit var trust: TextView
    private lateinit var empty: TextView
    private lateinit var badgePrimary: TextView
    private lateinit var badgeSecondary: TextView
    private lateinit var badgeTertiary: TextView
    private lateinit var metricProducts: TextView
    private lateinit var metricDelivery: TextView
    private lateinit var metricAvailable: TextView
    private var reviewCount = 0
    private var reviewAverage = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seller_profile)

        val sellerId = intent.getStringExtra("sellerId") ?: return finish()
        val btnBack = findViewById<Button>(R.id.btnBack)
        val sellerHeader = findViewById<View>(R.id.sellerHeader)

        btnBack.setOnClickListener { finish() }
        UiMotion.makePressable(btnBack)
        sellerName = findViewById(R.id.tvSellerName)
        stats = findViewById(R.id.tvSellerStats)
        trust = findViewById(R.id.tvSellerTrust)
        empty = findViewById(R.id.tvEmpty)
        badgePrimary = findViewById(R.id.tvSellerBadgePrimary)
        badgeSecondary = findViewById(R.id.tvSellerBadgeSecondary)
        badgeTertiary = findViewById(R.id.tvSellerBadgeTertiary)
        metricProducts = findViewById(R.id.tvSellerMetricProducts)
        metricDelivery = findViewById(R.id.tvSellerMetricDelivery)
        metricAvailable = findViewById(R.id.tvSellerMetricAvailable)
        sellerName.text = getString(R.string.local_producer)

        val list = findViewById<ListView>(R.id.listSellerProducts)
        UiMotion.reveal(btnBack, sellerHeader, list)
        adapter = object : BaseAdapter() {
            override fun getCount(): Int = products.size
            override fun getItem(position: Int): Product = products[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val isNewView = convertView == null
                val view = convertView ?: layoutInflater.inflate(R.layout.item_seller_product, parent, false)
                val product = products[position]

                view.findViewById<TextView>(R.id.tvName).text = product.name
                view.findViewById<TextView>(R.id.tvPrice).text = MarketFormat.formatMoney(
                    this@SellerProfileActivity,
                    product.getPriceAsDouble(),
                    product.normalizedCurrency()
                )
                val available = MarketFormat.formatQuantity(
                    this@SellerProfileActivity,
                    product.getAvailableStock(),
                    product.normalizedUnit()
                )
                view.findViewById<TextView>(R.id.tvCategory).text = listOf(
                    getString(ProductCategories.labelRes(product.category)),
                    getString(MarketplaceSignals.fulfillmentBadgeRes(product.normalizedFulfillmentMode())),
                    available
                ).joinToString(" - ")

                Glide.with(this@SellerProfileActivity)
                    .load(product.imageUrl.ifEmpty { "https://via.placeholder.com/300" })
                    .into(view.findViewById<ImageView>(R.id.imgProduct))
                UiMotion.makePressable(view)
                view.setOnClickListener {
                    EngagementTracker.productEvent("view_seller_profile_product", product)
                    startActivity(
                        Intent(this@SellerProfileActivity, DetailActivity::class.java)
                            .putExtra("productId", product.id)
                    )
                }
                if (isNewView) {
                    UiMotion.showSurface(view, fromY = 14f)
                }

                return view
            }
        }
        list.adapter = adapter

        loadReviews(sellerId)
        loadProducts(sellerId)
    }

    private fun loadReviews(sellerId: String) {
        db.collection("reviews")
            .whereEqualTo("sellerId", sellerId)
            .get()
            .addOnSuccessListener { result ->
                val ratings = result.documents.mapNotNull { it.getDouble("rating") }
                reviewCount = ratings.size
                reviewAverage = if (ratings.isEmpty()) 0.0 else ratings.average()
                updateStats()
            }
    }

    private fun loadProducts(sellerId: String) {
        db.collection("products")
            .whereEqualTo("sellerId", sellerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                products.clear()
                for (doc in snapshot) {
                    val product = doc.toObject(Product::class.java)
                    product.id = doc.id
                    products.add(product)
                }
                products.sortWith(compareByDescending<Product> { it.getAvailableStock() > 0.0 }
                    .thenByDescending { it.hasLocalDelivery() }
                    .thenByDescending { it.getReservationCountAsLong() + it.getFavoriteCountAsLong() })

                empty.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
                updateStats()
            }
    }

    private fun updateStats() {
        val ratingText = if (reviewCount == 0) {
            getString(R.string.seller_new)
        } else {
            getString(R.string.seller_rating, "%.1f".format(reviewAverage), reviewCount)
        }
        stats.text = getString(R.string.seller_stats, ratingText, products.size)
        updateTrustHeader()
    }

    private fun updateTrustHeader() {
        val sellerType = dominantSellerType()
        val deliveryCount = products.count { it.hasLocalDelivery() }
        val availableCount = products.count { it.getAvailableStock() > 0.0 }

        sellerName.text = getString(MarketplaceSignals.sellerTypeLabelRes(sellerType))
        badgePrimary.text = getString(MarketplaceSignals.sellerTypeBadgeRes(sellerType))
        badgeSecondary.text = if (deliveryCount > 0) {
            getString(R.string.seller_badge_delivery)
        } else {
            getString(R.string.seller_badge_local)
        }
        badgeTertiary.text = if (reviewCount > 0) {
            getString(R.string.seller_badge_rating, "%.1f".format(reviewAverage))
        } else {
            getString(R.string.seller_badge_fresh)
        }

        metricProducts.text = getString(R.string.seller_metric_products, products.size)
        metricDelivery.text = getString(R.string.seller_metric_delivery, deliveryCount)
        metricAvailable.text = getString(R.string.seller_metric_available, availableCount)

        trust.text = when {
            products.isEmpty() -> getString(R.string.seller_profile_trust_empty)
            reviewCount > 0 && reviewAverage >= 4.5 -> getString(R.string.seller_profile_trust_top)
            deliveryCount > 0 -> getString(R.string.seller_profile_trust_delivery)
            else -> getString(R.string.seller_profile_trust_active)
        }
    }

    private fun dominantSellerType(): String {
        return products
            .groupingBy { it.normalizedSellerType() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: MarketplaceSignals.SELLER_FARMER
    }
}

package com.huertocero.huertocero

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
    private lateinit var stats: TextView
    private lateinit var empty: TextView
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
        findViewById<TextView>(R.id.tvSellerName).text = getString(R.string.local_producer)
        stats = findViewById(R.id.tvSellerStats)
        empty = findViewById(R.id.tvEmpty)

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
                view.findViewById<TextView>(R.id.tvCategory).text =
                    getString(ProductCategories.labelRes(product.category))

                Glide.with(this@SellerProfileActivity)
                    .load(product.imageUrl.ifEmpty { "https://via.placeholder.com/300" })
                    .into(view.findViewById<ImageView>(R.id.imgProduct))
                UiMotion.makePressable(view)
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
    }
}

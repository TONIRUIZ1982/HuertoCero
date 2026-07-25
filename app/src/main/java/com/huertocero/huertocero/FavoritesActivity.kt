package com.huertocero.huertocero

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class FavoritesActivity : HuertoActivity() {

    private lateinit var listView: ListView
    private lateinit var tvEmpty: TextView
    private lateinit var btnBack: Button

    private val db = FirebaseFirestore.getInstance()
    private val favoriteItems = mutableListOf<FavoriteItem>()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        listView = findViewById(R.id.listFavorites)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnBack = findViewById(R.id.btnBack)
        val favoritesHeader = findViewById<View>(R.id.favoritesHeader)

        UiMotion.makePressable(btnBack)
        UiMotion.reveal(favoritesHeader, listView)
        btnBack.setOnClickListener { finish() }
        loadFavorites()
    }

    private fun loadFavorites() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("favorites")
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    tvEmpty.visibility = View.VISIBLE
                    listView.adapter = null
                    return@addOnSuccessListener
                } else {
                    tvEmpty.visibility = View.GONE
                }

                favoriteItems.clear()
                favoriteItems.addAll(result.documents.map { FavoriteItem.from(it) })

                adapter = object : BaseAdapter() {
                    override fun getCount(): Int = favoriteItems.size
                    override fun getItem(position: Int) = favoriteItems[position]
                    override fun getItemId(position: Int) = position.toLong()

                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val isNewView = convertView == null
                        val view = convertView ?: layoutInflater.inflate(R.layout.item_favorite, parent, false)
                        val name = view.findViewById<TextView>(R.id.tvFavoriteName)
                        val meta = view.findViewById<TextView>(R.id.tvFavoriteMeta)
                        val status = view.findViewById<TextView>(R.id.tvFavoriteStatus)
                        val btnOpen = view.findViewById<Button>(R.id.btnOpenProduct)
                        val btnDelete = view.findViewById<Button>(R.id.btnDelete)
                        val item = favoriteItems[position]
                        val product = item.product

                        val productName = product?.name ?: item.name
                        val price = product?.getPriceAsDouble() ?: item.price
                        val currency = product?.normalizedCurrency() ?: item.currency
                        val productId = item.productId

                        UiMotion.makePressable(view, btnOpen, btnDelete)
                        if (isNewView) {
                            UiMotion.showSurface(view, fromY = 14f)
                        }
                        name.text = productName
                        meta.text = favoriteMeta(product, price, currency)
                        status.text = favoriteStatus(product)
                        status.setTextColor(
                            ContextCompat.getColor(
                                this@FavoritesActivity,
                                if (product != null && product.getAvailableStock() <= 2.0) R.color.scarcity else R.color.brand_olive
                            )
                        )

                        val openProduct = View.OnClickListener {
                            startActivity(
                                Intent(this@FavoritesActivity, DetailActivity::class.java)
                                    .putExtra("productId", productId)
                            )
                        }
                        view.setOnClickListener(openProduct)
                        btnOpen.setOnClickListener(openProduct)

                        btnDelete.setOnClickListener {
                            db.collection("users")
                                .document(userId)
                                .collection("favorites")
                                .document(item.favoriteDocId)
                                .delete()

                            Toast.makeText(this@FavoritesActivity, getString(R.string.removed), Toast.LENGTH_SHORT).show()
                            loadFavorites()
                        }

                        return view
                    }
                }

                listView.adapter = adapter
                hydrateFavoriteProducts()
            }
    }

    private fun hydrateFavoriteProducts() {
        favoriteItems.forEachIndexed { index, item ->
            if (item.productId.isBlank()) return@forEachIndexed
            db.collection("products")
                .document(item.productId)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) return@addOnSuccessListener
                    val product = snapshot.toObject(Product::class.java) ?: return@addOnSuccessListener
                    product.id = snapshot.id
                    favoriteItems.getOrNull(index)?.product = product
                    adapter.notifyDataSetChanged()
                }
        }
    }

    private fun favoriteMeta(product: Product?, fallbackPrice: Double, fallbackCurrency: String): String {
        val price = MarketFormat.formatMoney(
            this,
            product?.getPriceAsDouble() ?: fallbackPrice,
            product?.normalizedCurrency() ?: fallbackCurrency
        )
        if (product == null) return "$price - ${getString(R.string.favorite_open_hint)}"

        return listOf(
            price,
            getString(ProductCategories.labelRes(product.category)),
            getString(MarketplaceSignals.fulfillmentBadgeRes(product.normalizedFulfillmentMode()))
        ).joinToString(" - ")
    }

    private fun favoriteStatus(product: Product?): String {
        if (product == null) return getString(R.string.favorite_status_loading)
        val available = product.getAvailableStock()
        return when {
            available <= 0.0 -> getString(R.string.favorite_status_sold_out)
            available <= 2.0 -> getString(
                R.string.favorite_status_low_stock,
                MarketFormat.formatQuantity(this, available, product.normalizedUnit())
            )
            else -> getString(
                R.string.favorite_status_available,
                MarketFormat.formatQuantity(this, available, product.normalizedUnit())
            )
        }
    }

    private data class FavoriteItem(
        val favoriteDocId: String,
        val productId: String,
        val name: String,
        val price: Double,
        val currency: String,
        var product: Product? = null
    ) {
        companion object {
            fun from(doc: DocumentSnapshot): FavoriteItem {
                return FavoriteItem(
                    favoriteDocId = doc.id,
                    productId = doc.getString("productId") ?: doc.id,
                    name = doc.getString("name").orEmpty(),
                    price = doc.getDouble("price") ?: 0.0,
                    currency = doc.getString("currency") ?: "EUR"
                )
            }
        }
    }
}

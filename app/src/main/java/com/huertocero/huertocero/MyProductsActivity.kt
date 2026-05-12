package com.huertocero.huertocero

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MyProductsActivity : HuertoActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val products = mutableListOf<Product>()
    private lateinit var adapter: BaseAdapter
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_products)

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            finish()
            return
        }

        tvEmpty = findViewById(R.id.tvEmpty)
        val list = findViewById<ListView>(R.id.listMyProducts)
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        adapter = object : BaseAdapter() {
            override fun getCount(): Int = products.size
            override fun getItem(position: Int): Product = products[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_my_product, parent, false)
                val product = products[position]

                val img = view.findViewById<ImageView>(R.id.imgProduct)
                val name = view.findViewById<TextView>(R.id.tvName)
                val price = view.findViewById<TextView>(R.id.tvPrice)
                val category = view.findViewById<TextView>(R.id.tvCategory)
                val stock = view.findViewById<TextView>(R.id.tvStock)
                val description = view.findViewById<TextView>(R.id.tvDescription)
                val btnEdit = view.findViewById<Button>(R.id.btnEdit)
                val btnDelete = view.findViewById<Button>(R.id.btnDelete)

                name.text = product.name
                price.text = MarketFormat.formatMoney(
                    this@MyProductsActivity,
                    product.getPriceAsDouble(),
                    product.normalizedCurrency()
                )
                category.text = getString(ProductCategories.labelRes(product.category))
                stock.text = getString(
                    R.string.reserved_stock,
                    MarketFormat.formatQuantity(this@MyProductsActivity, product.getStockReservedAsDouble(), product.normalizedUnit()),
                    MarketFormat.formatQuantity(this@MyProductsActivity, product.getStockTotalAsDouble(), product.normalizedUnit())
                )
                description.text = product.description.ifEmpty { getString(R.string.no_description) }

                Glide.with(this@MyProductsActivity)
                    .load(product.imageUrl.ifEmpty { "https://via.placeholder.com/300" })
                    .into(img)

                btnEdit.setOnClickListener { showEditDialog(product) }
                btnDelete.setOnClickListener { confirmDelete(product) }

                return view
            }
        }
        list.adapter = adapter

        db.collection("products")
            .whereEqualTo("sellerId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                products.clear()
                for (doc in snapshot) {
                    val product = doc.toObject(Product::class.java)
                    product.id = doc.id
                    products.add(product)
                }

                tvEmpty.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
            }
    }

    private fun showEditDialog(product: Product) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_product, null)
        val name = view.findViewById<EditText>(R.id.etName)
        val description = view.findViewById<EditText>(R.id.etDescription)
        val price = view.findViewById<EditText>(R.id.etPrice)
        val stockTotal = view.findViewById<EditText>(R.id.etStockTotal)
        val category = view.findViewById<Spinner>(R.id.spinnerCategory)
        val unit = view.findViewById<Spinner>(R.id.spinnerUnit)
        val currency = view.findViewById<Spinner>(R.id.spinnerCurrency)

        name.setText(product.name)
        description.setText(product.description)
        price.setText(product.getPriceAsDouble().toString())
        stockTotal.setText(product.getStockTotalAsDouble().toString())
        configureCategorySpinner(category, product.category)
        configureSimpleSpinner(unit, MarketFormat.units, product.normalizedUnit())
        configureSimpleSpinner(currency, MarketFormat.currencies, product.normalizedCurrency())

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_product))
            .setView(view)
            .setPositiveButton(getString(R.string.save), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = name.text.toString().trim()
                val newDescription = description.text.toString().trim()
                val newPrice = price.text.toString().toDoubleOrNull()
                val newStockTotal = stockTotal.text.toString().toDoubleOrNull()
                val newCategory = ProductCategories.all.getOrElse(category.selectedItemPosition) {
                    ProductCategories.OTHER
                }
                val newUnit = MarketFormat.units.getOrElse(unit.selectedItemPosition) { "kg" }
                val newCurrency = MarketFormat.currencies.getOrElse(currency.selectedItemPosition) { "EUR" }

                if (newName.isEmpty() || newPrice == null || newStockTotal == null || newStockTotal <= 0.0) {
                    Toast.makeText(this, getString(R.string.complete_name_price), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                db.collection("products")
                    .document(product.id)
                    .update(
                        mapOf(
                            "name" to newName,
                            "description" to newDescription,
                            "price" to newPrice,
                            "category" to ProductCategories.normalize(newCategory),
                            "stockTotal" to newStockTotal,
                            "unit" to MarketFormat.normalizeUnit(newUnit),
                            "currency" to MarketFormat.normalizeCurrency(newCurrency)
                        )
                    )
                    .addOnSuccessListener {
                        Toast.makeText(this, getString(R.string.product_updated), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, getString(R.string.product_update_error), Toast.LENGTH_SHORT).show()
                    }
            }
        }

        dialog.show()
    }

    private fun confirmDelete(product: Product) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_product))
            .setMessage(getString(R.string.delete_product_message, product.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                db.collection("products")
                    .document(product.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, getString(R.string.product_deleted), Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, getString(R.string.product_delete_error), Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun configureCategorySpinner(spinner: Spinner, selectedCategory: String) {
        val labels = ProductCategories.all.map { getString(ProductCategories.labelRes(it)) }
        spinner.adapter = readableSpinnerAdapter(labels)
        val selectedIndex = ProductCategories.all.indexOf(ProductCategories.normalize(selectedCategory))
        spinner.setSelection(selectedIndex.coerceAtLeast(0))
    }

    private fun configureSimpleSpinner(spinner: Spinner, values: List<String>, selectedValue: String) {
        spinner.adapter = readableSpinnerAdapter(values)
        spinner.setSelection(values.indexOf(selectedValue).coerceAtLeast(0))
    }

    private fun readableSpinnerAdapter(values: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, R.layout.item_spinner_readable, values).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown_readable)
        }
    }
}

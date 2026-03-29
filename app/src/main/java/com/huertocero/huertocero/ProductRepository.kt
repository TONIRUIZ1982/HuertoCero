package com.huertocero.huertocero

import com.google.firebase.firestore.FirebaseFirestore

class ProductRepository {

    private val db = FirebaseFirestore.getInstance()

    fun addProduct(product: Product) {
        db.collection("products")
            .add(product)
    }

    fun getProducts(onResult: (List<Product>) -> Unit) {
        db.collection("products")
            .get()
            .addOnSuccessListener { result ->

                val list: List<Product> = result.mapNotNull {
                    it.toObject(Product::class.java)
                }

                onResult(list)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }
}